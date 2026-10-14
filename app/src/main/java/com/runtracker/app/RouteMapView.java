package com.runtracker.app;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import android.view.*;

import org.json.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Native map: only visible OSM raster tiles are fetched; no API key or route upload. */
public class RouteMapView extends View {
    private JSONArray route = new JSONArray();
    private double centerX = .5, centerY = .5;
    private int zoom = 16;
    private boolean touched, active, mapLoaded, streetMapReady;
    private float lastX, lastY;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Set<String> loading = Collections.synchronizedSet(new HashSet<String>());
    private final Map<String, Long> retry = new ConcurrentHashMap<>();
    private final LruCache<String, Bitmap> cache =
            new LruCache<String, Bitmap>(8 * 1024 * 1024) {
                @Override
                protected int sizeOf(String k, Bitmap b) {
                    return b.getByteCount();
                }
            };
    private final ScaleGestureDetector pinch;

    public RouteMapView(Context c) {
        super(c);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Run route map. Drag to pan and pinch to zoom.");
        pinch =
                new ScaleGestureDetector(
                        c,
                        new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            private float accumulated = 1;

                            @Override
                            public boolean onScaleBegin(ScaleGestureDetector d) {
                                accumulated = 1;
                                return true;
                            }

                            @Override
                            public boolean onScale(ScaleGestureDetector d) {
                                accumulated *= d.getScaleFactor();
                                if (accumulated > 1.35) {
                                    changeZoom(1);
                                    accumulated = 1;
                                } else if (accumulated < .74) {
                                    changeZoom(-1);
                                    accumulated = 1;
                                }
                                return true;
                            }
                        });
    }

    public void setRoute(JSONArray points, boolean live) {
        boolean first = route.length() == 0;
        route = points;
        active = live;
        if (route.length() > 0 && !touched) {
            if (active || first) {
                JSONObject p = route.optJSONObject(route.length() - 1);
                if (p != null) {
                    centerX = RunMath.worldX(p.optDouble("longitude"));
                    centerY = RunMath.worldY(p.optDouble("latitude"));
                }
            }
            if (!active) fit();
        }
        invalidate();
    }

    public boolean isStreetMapReady() {
        return streetMapReady || route.length() == 0;
    }

    public void fit() {
        streetMapReady = false;
        touched = false;
        if (route.length() == 0) {
            invalidate();
            return;
        }
        double minX = 1, maxX = 0, minY = 1, maxY = 0;
        for (int i = 0; i < route.length(); i++) {
            JSONObject p = route.optJSONObject(i);
            if (p == null) continue;
            double x = RunMath.worldX(p.optDouble("longitude")),
                    y = RunMath.worldY(p.optDouble("latitude"));
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        centerX = (minX + maxX) / 2;
        centerY = (minY + maxY) / 2;
        zoom = 17;
        while (zoom > 3
                && ((maxX - minX) * world() > Math.max(100, getWidth() - 80)
                        || (maxY - minY) * world() > Math.max(100, getHeight() - 80))) zoom--;
        invalidate();
    }

    public void changeZoom(int delta) {
        touched = true;
        zoom = Math.max(3, Math.min(19, zoom + delta));
        invalidate();
    }

    public void showCloseUp() {
        fit();
        zoom = 18;
        touched = true;
        invalidate();
    }

    private double world() {
        return 256.0 * (1 << zoom);
    }

    private float screenX(double lon) {
        double difference = RunMath.worldX(lon) - centerX;
        if (difference > .5) difference -= 1;
        if (difference < -.5) difference += 1;
        return (float) (difference * world() + getWidth() / 2.0);
    }

    private float screenY(double lat) {
        return (float) ((RunMath.worldY(lat) - centerY) * world() + getHeight() / 2.0);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (!active && !touched) fit();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(Color.rgb(239, 242, 245));
        paint.setStrokeWidth(1);
        paint.setColor(Color.rgb(225, 230, 235));
        for (int x = 0; x < getWidth(); x += 40) c.drawLine(x, 0, x, getHeight(), paint);
        for (int y = 0; y < getHeight(); y += 40) c.drawLine(0, y, getWidth(), y, paint);
        if (route.length() == 0) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f - 18;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(Color.rgb(81, 103, 106));
            c.drawCircle(cx, cy, 32, paint);
            c.drawCircle(cx, cy, 14, paint);
            c.drawLine(cx - 44, cy, cx - 23, cy, paint);
            c.drawLine(cx + 23, cy, cx + 44, cy, paint);
            c.drawLine(cx, cy - 44, cx, cy - 23, paint);
            c.drawLine(cx, cy + 23, cx, cy + 44, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(14));
            paint.setColor(MainActivity.MUTED);
            c.drawText(
                    active ? "Waiting for your first GPS fix" : "Your next route starts here",
                    cx,
                    cy + 67,
                    paint);
            return;
        }
        double left = centerX * world() - getWidth() / 2.0,
                top = centerY * world() - getHeight() / 2.0;
        int firstX = (int) Math.floor(left / 256),
                lastX = (int) Math.floor((left + getWidth()) / 256);
        int firstY = (int) Math.floor(top / 256),
                lastY = (int) Math.floor((top + getHeight()) / 256);
        boolean visibleTile = false;
        streetMapReady = true;
        paint.setColor(Color.WHITE);
        for (int tx = firstX; tx <= lastX; tx++)
            for (int ty = firstY; ty <= lastY; ty++) {
                int n = 1 << zoom;
                if (ty < 0 || ty >= n) continue;
                int wrapped = ((tx % n) + n) % n;
                String key = zoom + "_" + wrapped + "_" + ty;
                Bitmap bitmap = cache.get(key);
                if (bitmap != null) {
                    visibleTile = true;
                    RectF rect =
                            new RectF(
                                    (float) (tx * 256 - left),
                                    (float) (ty * 256 - top),
                                    (float) ((tx + 1) * 256 - left),
                                    (float) ((ty + 1) * 256 - top));
                    c.drawBitmap(bitmap, null, rect, paint);
                } else {
                    streetMapReady = false;
                    load(key, zoom, wrapped, ty);
                }
            }
        mapLoaded = visibleTile;
        Path path = new Path();
        int priorSegment = Integer.MIN_VALUE;
        for (int i = 0; i < route.length(); i++) {
            JSONObject p = route.optJSONObject(i);
            if (p == null) continue;
            float x = screenX(p.optDouble("longitude")), y = screenY(p.optDouble("latitude"));
            int segment = p.optInt("segment", 0);
            if (i == 0 || segment != priorSegment) path.moveTo(x, y);
            else path.lineTo(x, y);
            priorSegment = segment;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(dp(7));
        c.drawPath(path, paint);
        paint.setColor(MainActivity.ACCENT);
        paint.setStrokeWidth(dp(4));
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        drawDot(c, route.optJSONObject(0), Color.WHITE);
        drawDot(c, route.optJSONObject(route.length() - 1), MainActivity.ACCENT);
        if (!mapLoaded) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(11));
            paint.setColor(MainActivity.MUTED);
            c.drawText(
                    "Street map loading / offline · GPS route still shown",
                    getWidth() / 2f,
                    getHeight() - dp(14),
                    paint);
        }
    }

    private void drawDot(Canvas c, JSONObject p, int color) {
        if (p == null) return;
        float x = screenX(p.optDouble("longitude")), y = screenY(p.optDouble("latitude"));
        paint.setColor(Color.WHITE);
        c.drawCircle(x, y, dp(7), paint);
        paint.setColor(color);
        c.drawCircle(x, y, dp(4.5f), paint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void load(String key, int z, int x, int y) {
        Long after = retry.get(key);
        if ((after != null && after > System.currentTimeMillis())
                || loading.size() >= 12
                || workers.isShutdown()
                || !loading.add(key)) return;
        workers.submit(
                () -> {
                    HttpURLConnection connection = null;
                    try {
                        File dir = new File(getContext().getCacheDir(), "map-tiles");
                        dir.mkdirs();
                        File file = new File(dir, key + ".png"),
                                meta = new File(dir, key + ".json");
                        JSONObject metadata = new JSONObject();
                        if (meta.exists())
                            try {
                                metadata = new JSONObject(readText(meta));
                            } catch (Exception ignored) {
                            }
                        Bitmap bitmap =
                                file.exists() ? BitmapFactory.decodeFile(file.getPath()) : null;
                        if (bitmap != null) {
                            cache.put(key, bitmap);
                            postInvalidate();
                        }
                        if (bitmap != null
                                && metadata.optLong("expires", 0) > System.currentTimeMillis())
                            return;
                        URL url =
                                new URL(
                                        "https://tile.openstreetmap.org/"
                                                + z
                                                + "/"
                                                + x
                                                + "/"
                                                + y
                                                + ".png");
                        connection = (HttpURLConnection) url.openConnection();
                        connection.setConnectTimeout(10000);
                        connection.setReadTimeout(10000);
                        connection.setRequestProperty(
                                "User-Agent",
                                "RunTracker-Android/0.2"
                                        + " (https://github.com/LeoNaveen10/run-tracker)");
                        if (metadata.has("etag"))
                            connection.setRequestProperty(
                                    "If-None-Match", metadata.optString("etag"));
                        if (metadata.optLong("lastModified", 0) > 0)
                            connection.setIfModifiedSince(metadata.optLong("lastModified"));
                        int code = connection.getResponseCode();
                        if (connection.getHeaderField("X-Blocked") != null)
                            throw new IOException("Map provider unavailable");
                        if (code == 200) {
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            try (InputStream in = connection.getInputStream()) {
                                byte[] buffer = new byte[8192];
                                int length;
                                while ((length = in.read(buffer)) != -1) {
                                    bytes.write(buffer, 0, length);
                                    if (bytes.size() > 2000000)
                                        throw new IOException("Tile too large");
                                }
                            }
                            byte[] data = bytes.toByteArray();
                            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                            if (bitmap == null) throw new IOException("Invalid map image");
                            try (FileOutputStream out = new FileOutputStream(file)) {
                                out.write(data);
                            }
                            cache.put(key, bitmap);
                        } else if (code != 304) throw new IOException("Map response " + code);
                        long expires = System.currentTimeMillis() + 7L * 24 * 3600 * 1000;
                        String control = connection.getHeaderField("Cache-Control");
                        if (control != null) {
                            if (control.contains("no-cache") || control.contains("must-revalidate"))
                                expires = System.currentTimeMillis();
                            for (String field : control.split(","))
                                if (field.trim().startsWith("max-age=")) {
                                    try {
                                        expires =
                                                System.currentTimeMillis()
                                                        + Long.parseLong(field.trim().substring(8))
                                                                * 1000;
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                        } else if (connection.getExpiration() > 0)
                            expires = connection.getExpiration();
                        metadata.put("expires", expires);
                        String tag = connection.getHeaderField("ETag");
                        if (tag != null) metadata.put("etag", tag);
                        if (connection.getLastModified() > 0)
                            metadata.put("lastModified", connection.getLastModified());
                        if (control != null && control.contains("no-store")) {
                            file.delete();
                            meta.delete();
                            return;
                        }
                        try (FileOutputStream out = new FileOutputStream(meta)) {
                            out.write(metadata.toString().getBytes("UTF-8"));
                        }
                        prune(dir, file);
                    } catch (Exception error) {
                        retry.put(key, System.currentTimeMillis() + 60000);
                    } finally {
                        if (connection != null) connection.disconnect();
                        loading.remove(key);
                        postInvalidate();
                    }
                });
    }

    private String readText(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        }
    }

    private void prune(File directory, File keep) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null || files.length <= 256) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - 256; i++)
            if (!files[i].equals(keep)) {
                files[i].delete();
                new File(directory, files[i].getName().replace(".png", ".json")).delete();
            }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        if (route.length() == 0) return false;
        getParent().requestDisallowInterceptTouchEvent(true);
        pinch.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!pinch.isInProgress() && e.getPointerCount() == 1) {
                    touched = true;
                    centerX -= (e.getX() - lastX) / world();
                    centerX = (centerX % 1 + 1) % 1;
                    centerY = Math.max(0, Math.min(1, centerY - (e.getY() - lastY) / world()));
                    invalidate();
                }
                lastX = e.getX();
                lastY = e.getY();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        workers.shutdownNow();
        super.onDetachedFromWindow();
    }
}
