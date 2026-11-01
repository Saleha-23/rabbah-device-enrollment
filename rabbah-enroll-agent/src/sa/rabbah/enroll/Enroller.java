package sa.rabbah.enroll;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Reads the serial number and calls POST /devices/enroll. */
final class Enroller {
    static final String TAG = "RabbahEnroll";

    // Baked into the build. Environments: dev-api / uat-api / api .rabbah.sa
    static final String DEFAULT_BASE_URL = "https://dev-api.rabbah.sa/api/v1";
    static final String DEFAULT_KEY = "";   // x-device-enrollment-key baked into the image
    static final String MDM_URL = "https://mdm.rabbah.sa";
    // Stand-in serial for a phone that cannot read its own. Must stay empty in builds that go to the team.
    static final String TEST_SERIAL = "";

    static final String PREFS = "enroll";
    static final String K_BASE = "base_url", K_KEY = "key", K_CODE = "device_code", K_STATUS = "status", K_SERIAL = "manual_serial",
            K_STATE = "state", K_TIME = "last_attempt";   // state: enrolled | not_registered | bad_key | no_serial | network | error

    enum Result { ENROLLED, STOP_BAD_KEY, RETRY }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String serial() {
        try {
            String s = Build.getSerial();               // needs READ_PRIVILEGED_PHONE_STATE (API 29+)
            if (s != null && !Build.UNKNOWN.equalsIgnoreCase(s)) return s;
        } catch (Throwable t) {
            Log.w(TAG, "Build.getSerial failed: " + t);
        }
        String legacy = Build.SERIAL;                   // works below API 29
        return (legacy == null || Build.UNKNOWN.equalsIgnoreCase(legacy)) ? null : legacy;
    }

    /** Blocking; call off the main thread. */
    static Result enroll(Context c) {
        SharedPreferences p = prefs(c);
        setState(p, "reading");
        String serial = serial();
        if (serial == null && !TEST_SERIAL.isEmpty()) serial = TEST_SERIAL;   // test builds only
        if (serial == null) return fail(p, Result.RETRY, "no_serial", "Serial number unavailable (app not privileged?)");
        setState(p, "requesting");

        String base = DEFAULT_BASE_URL;
        String key = DEFAULT_KEY;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base + "/devices/enroll").openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-device-enrollment-key", key);
            byte[] body = new JSONObject().put("serialNumber", serial).toString().getBytes("UTF-8");
            try (OutputStream os = conn.getOutputStream()) { os.write(body); }

            int status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            JSONObject json = parse(in);
            String message = json != null ? json.optString("message", "") : "";

            if (status == 200 && json != null) {
                String code = json.getJSONObject("data").getString("deviceCode");
                p.edit().putString(K_CODE, code).putString(K_STATE, "registering").apply();
                Log.i(TAG, "Device code received: " + code);
                if (mdmKnows(code)) {
                    p.edit().putString(K_STATUS, "Enrolled as " + code).putString(K_STATE, "enrolled")
                            .putLong(K_TIME, System.currentTimeMillis()).apply();
                    return Result.ENROLLED;
                }
                return fail(p, Result.RETRY, "mdm_pending", "Device " + code + " is not yet known to the MDM");
            }
            if (status == 401) return fail(p, Result.STOP_BAD_KEY, "bad_key", "401 " + message);
            if (status == 404) return fail(p, Result.RETRY, "not_registered", "404 " + message);
            return fail(p, Result.RETRY, "error", status + " " + message);   // retry in 15 min
        } catch (Exception e) {
            return fail(p, Result.RETRY, "network", "Network error: " + e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void setState(SharedPreferences p, String state) {
        p.edit().putString(K_STATE, state).apply();
    }

    /** True when the MDM already has a record for this device code (same public call the launcher makes). */
    private static boolean mdmKnows(String code) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(MDM_URL + "/rest/public/sync/configuration/"
                    + java.net.URLEncoder.encode(code, "UTF-8")).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            JSONObject json = parse(conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream());
            return json != null && "OK".equals(json.optString("status"));
        } catch (Exception e) {
            Log.w(TAG, "MDM check failed: " + e);
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static Result fail(SharedPreferences p, Result r, String state, String msg) {
        Log.w(TAG, msg);
        p.edit().putString(K_STATUS, msg).putString(K_STATE, state)
                .putLong(K_TIME, System.currentTimeMillis()).apply();
        return r;
    }

    private static JSONObject parse(InputStream in) {
        if (in == null) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }
}
