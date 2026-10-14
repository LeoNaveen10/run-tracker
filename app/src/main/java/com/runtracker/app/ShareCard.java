package com.runtracker.app;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class ShareCard {
    public static void share(Activity activity, JSONObject run, RouteMapView map) {
        map.fit();
        Toast.makeText(activity, "Preparing full-route image…", Toast.LENGTH_SHORT).show();
        waitForMap(activity, run, map, android.os.SystemClock.elapsedRealtime());
    }

    private static void waitForMap(
            Activity activity, JSONObject run, RouteMapView map, long started) {
        if (activity.isFinishing() || activity.isDestroyed() || !map.isAttachedToWindow()) return;
        if (map.isStreetMapReady()) {
            render(activity, run, map);
            return;
        }
        if (android.os.SystemClock.elapsedRealtime() - started >= 10000) {
            new android.app.AlertDialog.Builder(activity)
                    .setTitle("Street map unavailable")
                    .setMessage(
                            "You can share your recorded route outline and metrics, or try again"
                                    + " when the map loads.")
                    .setPositiveButton("Share outline", (d, w) -> render(activity, run, map))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        map.postDelayed(() -> waitForMap(activity, run, map, started), 250);
    }

    private static void render(Activity activity, JSONObject run, RouteMapView map) {
        Bitmap card = null;
        try {
            if (map.getWidth() <= 0 || map.getHeight() <= 0) {
                Toast.makeText(
                                activity,
                                "Wait for the map to appear, then share",
                                Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            int mapHeight = Math.round(1080f * map.getHeight() / map.getWidth());
            int metricsTop = 210 + mapHeight;
            double target = run.optDouble("targetKm", 0);
            boolean complete = target > 0 && run.optDouble("meters") >= target * 1000;
            int banner = complete ? 130 : 0;
            card = Bitmap.createBitmap(1080, metricsTop + banner + 530, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(card);
            canvas.drawColor(Color.WHITE);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            Calendar date = Calendar.getInstance();
            date.setTimeInMillis(run.optLong("date"));
            int hour = date.get(Calendar.HOUR_OF_DAY);
            String title =
                    hour < 5
                            ? "Night Run"
                            : hour < 12
                                    ? "Morning Run"
                                    : hour < 17
                                            ? "Afternoon Run"
                                            : hour < 21 ? "Evening Run" : "Night Run";
            text(canvas, paint, title, 48, 85, 58, MainActivity.TEXT);
            text(
                    canvas,
                    paint,
                    new SimpleDateFormat("EEE, d MMM yyyy · HH:mm", Locale.getDefault())
                            .format(date.getTime()),
                    48,
                    138,
                    25,
                    MainActivity.MUTED);
            canvas.save();
            canvas.translate(0, 180);
            canvas.scale(1080f / map.getWidth(), 1080f / map.getWidth());
            map.draw(canvas);
            canvas.restore();
            text(
                    canvas,
                    paint,
                    "© OpenStreetMap contributors",
                    24,
                    180 + mapHeight - 18,
                    20,
                    MainActivity.TEXT);
            if (complete) {
                paint.setColor(Color.rgb(255, 244, 236));
                canvas.drawRoundRect(48, metricsTop, 1032, metricsTop + 105, 14, 14, paint);
                text(
                        canvas,
                        paint,
                        "Target complete · " + GoalProgress.number(target) + " km",
                        76,
                        metricsTop + 65,
                        34,
                        MainActivity.ACCENT);
            }
            int top = metricsTop + banner;
            metric(
                    canvas,
                    paint,
                    "Distance",
                    String.format(Locale.US, "%.2f km", run.optDouble("meters") / 1000),
                    48,
                    top);
            metric(
                    canvas,
                    paint,
                    "Avg Pace",
                    RunMath.pace(run.optLong("duration"), run.optDouble("meters")) + " /km",
                    576,
                    top);
            metric(
                    canvas,
                    paint,
                    "Run Time",
                    RunMath.clock(run.optLong("duration")),
                    48,
                    top + 190);
            metric(
                    canvas,
                    paint,
                    "Target",
                    target > 0 ? GoalProgress.number(target) + " km" : "—",
                    576,
                    top + 190);
            paint.setColor(Color.rgb(230, 232, 235));
            paint.setStrokeWidth(2);
            canvas.drawLine(48, top + 168, 1032, top + 168, paint);
            canvas.drawLine(540, top + 20, 540, top + 140, paint);
            canvas.drawLine(540, top + 210, 540, top + 330, paint);
            text(canvas, paint, "RUN TRACKER", 48, top + 455, 25, MainActivity.ACCENT);
            File directory = new File(activity.getCacheDir(), "shares");
            if (!directory.isDirectory() && !directory.mkdirs())
                throw new IOException("Cannot prepare share image");
            File[] old = directory.listFiles();
            if (old != null)
                for (File file : old)
                    if (file.lastModified() < System.currentTimeMillis() - 7L * 24 * 3600 * 1000)
                        file.delete();
            String name = "run-" + UUID.randomUUID() + ".png";
            File file = new File(directory, name);
            try (FileOutputStream out = new FileOutputStream(file)) {
                if (!card.compress(Bitmap.CompressFormat.PNG, 100, out))
                    throw new IOException("Could not write image");
            }
            Uri uri =
                    new Uri.Builder()
                            .scheme("content")
                            .authority("com.runtracker.app.share")
                            .appendPath(name)
                            .build();
            Intent send =
                    new Intent(Intent.ACTION_SEND)
                            .setType("image/png")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .putExtra(
                                    Intent.EXTRA_TEXT,
                                    String.format(
                                            Locale.US,
                                            "My run: %.2f km · %s · %s min/km",
                                            run.optDouble("meters") / 1000,
                                            RunMath.clock(run.optLong("duration")),
                                            RunMath.pace(
                                                    run.optLong("duration"),
                                                    run.optDouble("meters"))))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(
                    ClipData.newUri(activity.getContentResolver(), "Run route and metrics", uri));
            activity.startActivity(Intent.createChooser(send, "Share your run"));
        } catch (Exception error) {
            Toast.makeText(
                            activity,
                            "Could not share this run. Check storage and available sharing apps.",
                            Toast.LENGTH_LONG)
                    .show();
        } finally {
            if (card != null) card.recycle();
        }
    }

    private static void metric(
            Canvas canvas, Paint paint, String label, String value, int x, int top) {
        text(canvas, paint, label, x, top + 46, 29, MainActivity.MUTED);
        text(canvas, paint, value, x, top + 116, 48, MainActivity.TEXT);
    }

    private static void text(
            Canvas canvas, Paint paint, String value, int x, int y, int size, int color) {
        paint.setTextSize(size);
        paint.setColor(color);
        canvas.drawText(value, x, y, paint);
    }
}
