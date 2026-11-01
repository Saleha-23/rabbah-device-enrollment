package com.hmdm.launcher.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.R;
import com.hmdm.launcher.util.RabbahEnroll;

import java.util.Locale;

/** Rabbah-branded enrollment progress screen. Read-only: enrollment runs by itself, nothing is pressed or typed. */
public class RabbahEnrollActivity extends Activity {
    // Rabbah brand palette (rabbah.sa)
    private static final int PURPLE = 0xFF574EAD, INK = 0xFF16151F, MUTED = 0xFF6E6B84,
            LINE = 0xFFE4E2EF, PAGE = 0xFFF7F6FC, TINT = 0xFFEDECF7, AMBER = 0xFFE8A93C,
            GREEN = 0xFF1E8E5A, RED = 0xFFC0392B, WHITE = 0xFFFFFFFF;
    private static final int DONE = 0, ACTIVE = 1, FAILED = 2, PENDING = 3;

    private LinearLayout content;
    private boolean closing;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(PURPLE);

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

    @Override protected void onResume() {
        super.onResume();
        RabbahEnroll.setListener(this::render);
        render();
    }

    @Override protected void onPause() {
        super.onPause();
        RabbahEnroll.setListener(null);
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

        TextView env = text(envName().toUpperCase(Locale.US), 11, INK, true);
        env.setBackground(rounded(AMBER, 99, 0, 0));
        env.setPadding(dp(10), dp(4), dp(10), dp(4));
        bar.addView(env);
        return bar;
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        String state = RabbahEnroll.state();
        String code = RabbahEnroll.deviceCode();
        content.addView(statusCard(state, code));
        content.addView(checklistCard(state, code));

        TextView foot = text("rabbah.sa  ·  v1.0", 12, MUTED, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(20), 0, 0);
        content.addView(foot);

        if (state.equals("enrolled") && !closing) {
            closing = true;   // hand over to the launcher once the checks have been seen
            new Handler(Looper.getMainLooper()).postDelayed(() -> { if (!isFinishing()) finish(); }, 2000);
        }
    }

    private View statusCard(String state, final String code) {
        int color; String glyph, title, sub;
        switch (state) {
            case "reading": case "requesting": case "registering":
                color = PURPLE; glyph = "•"; title = "Enrolling device";
                sub = "This device is enrolling itself. No action is needed."; break;
            case "enrolled":
                color = GREEN; glyph = "✓"; title = "Device enrolled";
                sub = "This device is registered with Rabbah and the MDM."; break;
            case "mdm_pending":
                color = AMBER; glyph = "!"; title = "Waiting for MDM";
                sub = "The device code was received, but the MDM does not know this device yet. It will retry automatically every 15 minutes."; break;
            case "not_registered":
                color = AMBER; glyph = "!"; title = "Waiting for registration";
                sub = "This device's serial number is not registered in Rabbah yet. It will retry automatically every 15 minutes."; break;
            case "bad_key":
                color = RED; glyph = "!"; title = "Enrollment key rejected";
                sub = "The enrollment key is missing or incorrect. Please contact the Rabbah team."; break;
            case "no_serial":
                color = AMBER; glyph = "!"; title = "Serial number unavailable";
                sub = "This app needs system privileges to read the device's serial number."; break;
            case "network":
                color = AMBER; glyph = "!"; title = "No connection";
                sub = "Rabbah could not be reached. It will retry automatically every 15 minutes."; break;
            case "error":
                color = RED; glyph = "!"; title = "Something went wrong";
                sub = RabbahEnroll.message() + " It will retry automatically."; break;
            default:
                color = PURPLE; glyph = "•"; title = "Starting";
                sub = "Enrollment starts automatically."; break;
        }

        LinearLayout card = card();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(rounded((color & 0x00FFFFFF) | 0x26000000, 24, 0, 0));
        TextView g = text(glyph, 24, color, true);
        g.setGravity(Gravity.CENTER);
        badge.addView(g, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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

    /** Static progress list: filled check = done, ring = in progress, cross = failed, grey ring = not started. */
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
        LinearLayout card = card();
        card.addView(text("PROGRESS", 11, MUTED, true));
        card.addView(step(s[0], "Serial number read", s[0] == FAILED ? "Not available on this device" : null));
        card.addView(step(s[1], "Device code requested from Rabbah", s[1] == FAILED ? failReason(state) : null));
        card.addView(step(s[2], "Device code received", s[2] == DONE && code != null ? code : null));
        card.addView(step(s[3], "Registered with MDM", s[3] == FAILED ? "Waiting for the MDM to know this device"
                : s[3] == DONE ? BuildConfig.BASE_URL.replace("https://", "") : null));
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
            if (status == DONE && detail.matches("[A-Z]-.*")) dt.setTypeface(Typeface.MONOSPACE);
            t.addView(dt);
        }
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    // ---------------------------------------------------------------- helpers

    private String envName() {
        String u = BuildConfig.RABBAH_API_URL;
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
}
