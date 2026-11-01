package sa.rabbah.enroll;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Rabbah-branded enrollment status screen. Enrollment starts automatically; nothing has to be typed. */
public class EnrollActivity extends Activity {
    // Rabbah brand palette (rabbah.sa)
    private static final int PURPLE = 0xFF574EAD, INK = 0xFF16151F, TEXT = 0xFF3B3849, MUTED = 0xFF6E6B84,
            LINE = 0xFFE4E2EF, PAGE = 0xFFF7F6FC, TINT = 0xFFEDECF7, AMBER = 0xFFE8A93C,
            GREEN = 0xFF1E8E5A, RED = 0xFFC0392B, WHITE = 0xFFFFFFFF;

    private SharedPreferences p;
    private boolean busy;
    private LinearLayout content;
    private TextView envChip;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = Enroller.prefs(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAGE);
        root.addView(header());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        render();
    }

    // ---------------------------------------------------------------- screen

    private View header() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(PURPLE);
        bar.setPadding(dp(20), dp(16), dp(20), dp(18));

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.rabbah_mark_white);
        bar.addView(mark, new LinearLayout.LayoutParams(dp(34), dp(39)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        titles.addView(text("Rabbah", 20, WHITE, true));
        titles.addView(text("Device Enrollment", 13, 0xCCFFFFFF, false));
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        envChip = text("", 11, INK, true);
        envChip.setBackground(rounded(AMBER, 99, 0, 0));
        envChip.setPadding(dp(10), dp(4), dp(10), dp(4));
        bar.addView(envChip);
        return bar;
    }

    private void render() {
        content.removeAllViews();
        String state = p.getString(Enroller.K_STATE, "idle");
        String code = p.getString(Enroller.K_CODE, null);
        envChip.setText(envName().toUpperCase(Locale.US));

        content.addView(statusCard(state, code));
        content.addView(checklistCard(state, code));
        if (state.equals("enrolled")) openLauncherOnce();

        TextView foot = text("rabbah.sa  ·  v1.0", 12, MUTED, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(20), 0, 0);
        content.addView(foot);
    }

    private View statusCard(String state, final String code) {
        int color; String glyph, title, sub;
        switch (state) {
            case "reading": case "requesting": case "registering":
                color = PURPLE; glyph = "•"; title = "Enrolling device";
                sub = "This device is enrolling itself. No action is needed."; break;
            case "mdm_pending":
                color = AMBER; glyph = "!"; title = "Waiting for MDM";
                sub = "The device code was received, but the MDM does not know this device yet. It will retry automatically every 15 minutes."; break;
            case "enrolled":
                color = GREEN; glyph = "✓"; title = "Device enrolled";
                sub = "This device is registered with Rabbah."; break;
            case "not_registered":
                color = AMBER; glyph = "!"; title = "Waiting for registration";
                sub = "This device's serial number is not registered in Rabbah yet. It will retry automatically every 15 minutes."; break;
            case "bad_key":
                color = RED; glyph = "!"; title = "Enrollment key rejected";
                sub = "The enrollment key is missing or incorrect. Please contact the Rabbah team."; break;
            case "no_serial":
                color = AMBER; glyph = "!"; title = "Serial number unavailable";
                sub = "Rabbah Enrollment needs system privileges to read this device's serial number."; break;
            case "network":
                color = AMBER; glyph = "!"; title = "No connection";
                sub = "Rabbah could not be reached. It will retry automatically every 15 minutes."; break;
            case "error":
                color = RED; glyph = "!"; title = "Something went wrong";
                sub = p.getString(Enroller.K_STATUS, "Unexpected response.") + ". It will retry automatically."; break;
            default:
                color = PURPLE; glyph = "•"; title = "Ready to enroll";
                sub = "Enrollment starts automatically."; break;
        }

        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayoutBadge badge = new FrameLayoutBadge(this, color, glyph);
        row.addView(badge, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), 0, 0, 0);
        t.addView(text(title, 18, INK, true));
        TextView s = text(sub, 14, MUTED, false);
        s.setPadding(0, dp(3), 0, 0);
        t.addView(s);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(row);

        boolean haveCode = code != null && (state.equals("enrolled") || state.equals("registering") || state.equals("mdm_pending"));
        if (haveCode) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackground(rounded(TINT, 14, 0, 0));
            box.setPadding(dp(16), dp(14), dp(16), dp(14));
            TextView label = text("DEVICE CODE", 11, MUTED, true);
            label.setLetterSpacing(0.1f);
            box.addView(label);
            TextView c = text(code, 34, PURPLE, true);
            c.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            box.addView(c);
            TextView copy = text("Copy code", 13, PURPLE, true);
            copy.setPadding(0, dp(6), 0, 0);
            copy.setOnClickListener(v -> {
                ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE))
                        .setPrimaryClip(ClipData.newPlainText("Rabbah device code", code));
                Toast.makeText(this, "Device code copied", Toast.LENGTH_SHORT).show();
            });
            box.addView(copy);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(16);
            card.addView(box, lp);
        }
        return card;
    }

    private boolean launcherOpened;

    /** Once the device is registered with the MDM, open the Headwind launcher (after a short pause so the checks are seen). */
    private void openLauncherOnce() {
        if (launcherOpened) return;
        launcherOpened = true;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.hmdm.launcher");
            if (i != null && !isFinishing()) startActivity(i);
        }, 2000);
    }

    private static final int DONE = 0, ACTIVE = 1, FAILED = 2, PENDING = 3;

    /** Static progress list: filled check = done, ring = in progress, cross = failed, grey ring = not started. No animation. */
    private View checklistCard(String state, String code) {
        int[] s = {PENDING, PENDING, PENDING, PENDING};
        switch (state) {
            case "reading":     s[0] = ACTIVE; break;
            case "requesting":  s[0] = DONE; s[1] = ACTIVE; break;
            case "registering": s[0] = s[1] = s[2] = DONE; s[3] = ACTIVE; break;
            case "enrolled":    s[0] = s[1] = s[2] = s[3] = DONE; break;
            case "mdm_pending": s[0] = s[1] = s[2] = DONE; s[3] = FAILED; break;
            case "no_serial":   s[0] = FAILED; break;
            case "not_registered": case "bad_key": case "network": case "error":
                s[0] = DONE; s[1] = FAILED; break;
            default: break;
        }
        boolean got = s[2] == DONE;
        LinearLayout card = card();
        card.addView(text("PROGRESS", 11, MUTED, true));
        card.addView(step(s[0], "Serial number read", s[0] == FAILED ? "Not available on this device" : null));
        card.addView(step(s[1], "Device code requested from Rabbah", s[1] == FAILED ? failReason(state) : null));
        card.addView(step(s[2], "Device code received", got && code != null ? code : null));
        card.addView(step(s[3], "Registered with MDM", s[3] == FAILED ? "Waiting for the MDM to know this device"
                : s[3] == DONE ? Enroller.MDM_URL.replace("https://", "") : null));
        return card;
    }

    private String failReason(String state) {
        switch (state) {
            case "not_registered": return "Serial number is not registered in Rabbah yet";
            case "bad_key": return "Enrollment key rejected";
            case "network": return "Rabbah could not be reached";
            default: return "Unexpected response";
        }
    }

    private View step(int status, String label, String detail) {
        int color = status == DONE ? GREEN : status == FAILED ? RED : status == ACTIVE ? PURPLE : MUTED;
        String glyph = status == DONE ? "✓" : status == FAILED ? "✕" : "";
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(14), 0, 0);

        TextView mark = text(glyph, 14, status == DONE || status == FAILED ? WHITE : color, true);
        mark.setGravity(Gravity.CENTER);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (status == DONE || status == FAILED) d.setColor(color);
        else { d.setColor(WHITE); d.setStroke(dp(2), color); }
        mark.setBackground(d);
        r.addView(mark, new LinearLayout.LayoutParams(dp(26), dp(26)));

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), 0, 0, 0);
        t.addView(text(label, 15, status == PENDING ? MUTED : INK, status == ACTIVE));
        if (detail != null) {
            TextView dt = text(detail, 13, status == FAILED ? RED : MUTED, false);
            if (status == DONE && detail.matches("D-.*")) dt.setTypeface(Typeface.MONOSPACE);
            t.addView(dt);
        }
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    // ---------------------------------------------------------------- enrollment

    private void runEnroll() {
        if (busy) return;
        busy = true;
        render();
        new Thread(() -> {
            Enroller.Result r = Enroller.enroll(getApplicationContext());
            if (r == Enroller.Result.RETRY) EnrollJobService.schedule(getApplicationContext());
            else EnrollJobService.cancel(getApplicationContext());
            runOnUiThread(() -> { busy = false; render(); });
        }).start();
    }

    private void autoEnroll() {
        if ("enrolled".equals(p.getString(Enroller.K_STATE, null))) return;
        // Android 6-9 needs READ_PHONE_STATE granted at runtime before the serial is readable
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 1);
            return;
        }
        runEnroll();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        runEnroll();
    }

    // re-draw whenever the enrollment progress changes (the screen updates; nothing is animated)
    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (sp, key) -> runOnUiThread(this::render);

    @Override protected void onResume() {
        super.onResume();
        p.registerOnSharedPreferenceChangeListener(listener);
        render();
        autoEnroll();
    }

    @Override protected void onPause() {
        super.onPause();
        p.unregisterOnSharedPreferenceChangeListener(listener);
    }

    // ---------------------------------------------------------------- helpers

    private String envName() {
        String u = Enroller.DEFAULT_BASE_URL;
        if (u.contains("dev-api")) return "Development";
        if (u.contains("uat-api")) return "UAT";
        if (u.contains("//api.rabbah.sa")) return "Production";
        return "Custom";
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(WHITE, 16, LINE, 1));
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    /** Round status badge with a glyph. */
    private final class FrameLayoutBadge extends android.widget.FrameLayout {
        FrameLayoutBadge(Context c, int color, String glyph) {
            super(c);
            setBackground(rounded((color & 0x00FFFFFF) | 0x26000000, 24, 0, 0));
            TextView g = text(glyph, 24, color, true);
            g.setGravity(Gravity.CENTER);
            addView(g, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }
}
