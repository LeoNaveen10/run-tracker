package com.runtracker.app;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;

import java.util.Locale;

/** Dedicated recording page: distance, current pace, elapsed time and the target. */
public class RunActivity extends Activity {
    private TextView distance, currentPace, time, status, target;
    private Button pause;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick =
            new Runnable() {
                @Override
                public void run() {
                    refresh();
                    if (!isFinishing()) handler.postDelayed(this, 500);
                }
            };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("run", 0);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = column();
        root.setBackgroundColor(MainActivity.BG);
        root.setPadding(dp(20), dp(12), dp(20), dp(16));
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30)
            root.setOnApplyWindowInsetsListener(
                    (v, insets) -> {
                        Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                        root.setPadding(
                                dp(20) + bars.left,
                                dp(12) + bars.top,
                                dp(20) + bars.right,
                                dp(16) + bars.bottom);
                        return insets;
                    });
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("YOUR RUN", 13, MainActivity.TEXT);
        title.setLetterSpacing(.12f);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header);
        status = label("Finding GPS…", 12, MainActivity.MUTED);
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status);
        LinearLayout stats = column();
        stats.setPadding(0, dp(12), 0, dp(16));
        distance = bigMetric(stats, "DISTANCE · KM", 68, MainActivity.ACCENT);
        currentPace = bigMetric(stats, "CURRENT PACE · MIN/KM", 62, MainActivity.TEXT);
        time = bigMetric(stats, "RUN TIME", 54, MainActivity.TEXT);
        target = bigMetric(stats, "TARGET · KM", 44, MainActivity.ACCENT);
        root.addView(stats, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = new LinearLayout(this);
        pause =
                button(
                        "Ⅱ  PAUSE",
                        MainActivity.ACCENT,
                        MainActivity.BG,
                        v -> command(prefs.getBoolean("paused", false) ? "RESUME" : "PAUSE"));
        Button finish =
                button(
                        "■  FINISH",
                        MainActivity.CARD,
                        MainActivity.TEXT,
                        v ->
                                new AlertDialog.Builder(this)
                                        .setTitle("Save this run?")
                                        .setMessage(
                                                "Your distance, time and route will appear in"
                                                        + " Recent runs.")
                                        .setPositiveButton("Save run", (d, w) -> command("FINISH"))
                                        .setNegativeButton("Keep running", null)
                                        .show());
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(0, dp(56), 1);
        pauseParams.rightMargin = dp(10);
        controls.addView(pause, pauseParams);
        controls.addView(finish, new LinearLayout.LayoutParams(0, dp(56), 1));
        root.addView(controls);
    }

    private void refresh() {
        if (!prefs.getBoolean("active", false)) {
            finish();
            return;
        }
        boolean paused = prefs.getBoolean("paused", false),
                waiting = prefs.getBoolean("waiting_gps", false);
        long elapsed = prefs.getLong("accumulated", 0);
        if (!paused && !waiting)
            elapsed +=
                    Math.max(
                            0,
                            SystemClock.elapsedRealtime()
                                    - prefs.getLong("segment", SystemClock.elapsedRealtime()));
        distance.setText(String.format(Locale.US, "%.2f", prefs.getFloat("meters", 0) / 1000.0));
        time.setText(RunMath.clock(elapsed));
        long speedFix = prefs.getLong("speed_fix_elapsed", 0),
                age = SystemClock.elapsedRealtime() - speedFix;
        currentPace.setText(
                paused
                                || waiting
                                || !prefs.getBoolean("pace_ready", false)
                                || speedFix == 0
                                || age > 5000
                                || age < 0
                        ? "—"
                        : RunMath.currentPace(prefs.getFloat("smoothed_speed_kmh", 0)));
        pause.setText(paused ? "▶  RESUME" : "Ⅱ  PAUSE");
        long fix = prefs.getLong("last_fix_elapsed", 0);
        boolean stale = fix != 0 && SystemClock.elapsedRealtime() - fix > 5000;
        String tracking = prefs.getString("status", "Finding GPS…");
        status.setText(
                tracking.startsWith("Could not save")
                        ? tracking
                        : paused
                                ? "PAUSED · resume when you're ready"
                                : waiting
                                        ? "Acquiring GPS · time starts after a fix"
                                        : stale
                                                ? "GPS signal lost · finding a fresh fix"
                                                : prefs.getFloat("accuracy", 999) > 20
                                                        ? "Weak GPS signal · waiting for a clearer"
                                                                + " fix"
                                                        : tracking.startsWith("GPS live")
                                                                ? (prefs.getBoolean(
                                                                                "pace_ready", false)
                                                                        ? "●  GPS LIVE"
                                                                        : "●  GPS LIVE · averaging"
                                                                              + " pace…")
                                                                : tracking);
        status.setTextColor(
                paused
                        ? MainActivity.MUTED
                        : waiting || stale || prefs.getFloat("accuracy", 999) > 20
                                ? Color.rgb(155, 97, 16)
                                : MainActivity.ACCENT);
        double goal = prefs.getFloat("target_km", 0);
        target.setText(goal > 0 ? GoalProgress.number(goal) : "—");
    }

    private void command(String action) {
        startService(new Intent(this, RunService.class).setAction(action));
        handler.postDelayed(() -> refresh(), 200);
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Keep recording in the background?")
                .setMessage("Your run will continue. Tap the recording notification to return.")
                .setPositiveButton("Keep recording", (d, w) -> moveTaskToBack(true))
                .setNegativeButton("Stay here", null)
                .show();
    }

    private TextView bigMetric(LinearLayout container, String title, int size, int color) {
        LinearLayout cell = column();
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(shape(MainActivity.CARD, 20));
        TextView value = metric("—", size);
        value.setTextColor(color);
        cell.addView(value, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView name = label(title, 11, MainActivity.MUTED);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, 0, 0, dp(10));
        cell.addView(name);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 0, 1);
        params.bottomMargin = dp(10);
        container.addView(cell, params);
        return value;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    private TextView metric(String value, int size) {
        TextView view = label(value, size, MainActivity.TEXT);
        view.setGravity(Gravity.CENTER);
        view.setMaxLines(1);
        view.setFontFeatureSettings("tnum");
        view.setAutoSizeTextTypeUniformWithConfiguration(12, size, 1, TypedValue.COMPLEX_UNIT_SP);
        return view;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private Button button(
            String value, int background, int foreground, View.OnClickListener click) {
        Button view = new Button(this);
        view.setText(value);
        view.setTextSize(13);
        view.setTextColor(foreground);
        view.setAllCaps(false);
        view.setBackground(
                new RippleDrawable(
                        android.content.res.ColorStateList.valueOf(Color.argb(45, 255, 255, 255)),
                        shape(background, 18),
                        null));
        view.setOnClickListener(click);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }
}
