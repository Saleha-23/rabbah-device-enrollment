package com.hmdm.launcher.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.Const;
import com.hmdm.launcher.helper.SettingsHelper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Rabbah auto-enrollment: sends the device serial number to Rabbah (POST /devices/enroll),
 * receives the device code (e.g. D-0001), checks the MDM knows it, and uses it as the Headwind device ID.
 * No user interaction. Retries every 15 minutes on 404 / network errors; stops on 401.
 *
 * Progress is published as a state so the Rabbah progress screen can show it:
 * reading, requesting, registering, enrolled | no_serial, not_registered, bad_key, network, error, mdm_pending
 */
public class RabbahEnroll {
    private static final String TAG = Const.LOG_TAG;
    private static final long RETRY_MS = 15 * 60 * 1000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        /** Called on the main thread once the device ID has been saved. */
        void onEnrolled(String deviceCode);
    }

    public interface Status {
        /** Called on the main thread whenever the progress state changes. */
        void onStatus(String message);
    }

    public interface Listener {
        /** Called on the main thread whenever the progress state changes. */
        void onChanged();
    }

    private static boolean running = false;
    private static volatile int lastStatus = 0;
    private static volatile String lastMessage = "";
    private static volatile String state = "idle";
    private static volatile String deviceCode = null;
    private static Listener listener;
    private static Runnable pendingRetry;

    public static boolean isEnabled() {
        return "rabbah".equals(BuildConfig.DEVICE_ID_CHOICE);
    }

    public static synchronized boolean isRunning() { return running; }

    public static String state() { return state; }
    public static String deviceCode() { return deviceCode; }
    public static String message() { return lastMessage; }

    public static void setListener(Listener l) { listener = l; }

    private static void set(String s) {
        state = s;
        MAIN.post(() -> { Listener l = listener; if (l != null) l.onChanged(); });
    }

    public static synchronized void start(final Context context, final Callback callback, final Status status) {
        if (running) return;
        running = true;
        if (pendingRetry != null) MAIN.removeCallbacks(pendingRetry);   // never stack retries
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            String code = enrollOnce();
            if (code != null && SettingsHelper.getInstance(app).setDeviceId(code)) {
                running = false;
                set("enrolled");
                MAIN.post(() -> callback.onEnrolled(code));
            } else if (code == null && lastStatus == 401) {
                running = false;   // wrong key: retrying will not help
            } else {
                running = false;
                MAIN.post(() -> status.onStatus(lastMessage + "\nRetrying in 15 minutes."));
                pendingRetry = () -> start(app, callback, status);
                MAIN.postDelayed(pendingRetry, RETRY_MS);
            }
        }).start();
    }

    /** Returns the device code once the MDM knows it, else null (state and lastMessage say why). */
    private static String enrollOnce() {
        set("reading");
        String serial = DeviceInfoProvider.getSerialNumber();
        boolean unreadable = serial == null || serial.length() == 0 || "unknown".equalsIgnoreCase(serial);
        if (unreadable && BuildConfig.RABBAH_TEST_SERIAL.length() > 0) {
            serial = BuildConfig.RABBAH_TEST_SERIAL;   // test builds only
            unreadable = false;
        }
        if (unreadable) {
            Log.w(TAG, "Rabbah enroll: serial number unavailable");
            lastStatus = 0;
            lastMessage = "Serial number unavailable.\nThis app needs device-owner or system privileges.";
            set("no_serial");
            return null;
        }
        set("requesting");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(BuildConfig.RABBAH_API_URL + "/devices/enroll").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-device-enrollment-key", BuildConfig.RABBAH_ENROLL_KEY);
            byte[] body = new JSONObject().put("serialNumber", serial).toString().getBytes("UTF-8");
            try (OutputStream os = conn.getOutputStream()) { os.write(body); }

            int status = conn.getResponseCode();
            lastStatus = status;
            String text = read(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (status == 200) {
                String code = new JSONObject(text).getJSONObject("data").getString("deviceCode");
                Log.i(TAG, "Rabbah enroll OK, device code " + code);
                deviceCode = code;
                set("registering");
                if (mdmKnows(code)) return code;
                lastMessage = "Device " + code + " is not yet known to the MDM.";
                set("mdm_pending");
                return null;
            }
            Log.w(TAG, "Rabbah enroll failed: " + status + " " + text);
            if (status == 401) { lastMessage = "Enrollment key rejected (401).\nPlease contact the Rabbah team."; set("bad_key"); }
            else if (status == 404 && text.contains("Cannot POST")) { lastMessage = "The Rabbah enrollment API is not available on this environment yet (404)."; set("error"); }
            else if (status == 404) { lastMessage = "This device's serial number (" + serial + ") is not registered in Rabbah yet (404)."; set("not_registered"); }
            else { lastMessage = "Rabbah returned an unexpected response (" + status + ")."; set("error"); }
        } catch (Exception e) {
            lastStatus = 0;
            lastMessage = "Rabbah could not be reached.\nCheck the network connection.";
            Log.w(TAG, "Rabbah enroll error: " + e);
            set("network");
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    /** True when the MDM already has a record for this device code (same public call the launcher makes next). */
    private static boolean mdmKnows(String code) {
        HttpURLConnection conn = null;
        try {
            String base = BuildConfig.BASE_URL;
            if (BuildConfig.SERVER_PROJECT.length() > 0) base += "/" + BuildConfig.SERVER_PROJECT;
            conn = (HttpURLConnection) new URL(base + "/rest/public/sync/configuration/"
                    + URLEncoder.encode(code, "UTF-8")).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            String text = read(conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream());
            return "OK".equals(new JSONObject(text).optString("status"));
        } catch (Exception e) {
            Log.w(TAG, "MDM check failed: " + e);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[2048];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
}
