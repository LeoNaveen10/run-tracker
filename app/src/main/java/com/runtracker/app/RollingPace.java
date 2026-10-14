package com.runtracker.app;

import java.util.ArrayList;

/** Time-weighted eight-second speed window, published at five-second intervals. */
public final class RollingPace {
    private static final long WINDOW = 8000, INTERVAL = 5000;

    private static final class Sample {
        final long time;
        final double speed;

        Sample(long t, double s) {
            time = t;
            speed = s;
        }
    }

    private final ArrayList<Sample> samples = new ArrayList<>();
    private long publishedAt;
    private double published = Double.NaN;

    public void reset() {
        samples.clear();
        published = Double.NaN;
        publishedAt = 0;
    }

    public double update(long time, double speed) {
        if (time < 0 || !Double.isFinite(speed) || speed < 0) return published;
        if (!samples.isEmpty()) {
            long last = samples.get(samples.size() - 1).time;
            if (time <= last) return published;
            if (time - last > INTERVAL) reset();
        }
        samples.add(new Sample(time, speed));
        long cutoff = time - WINDOW;
        while (samples.size() > 1 && samples.get(1).time <= cutoff) samples.remove(0);
        long start = Math.max(cutoff, samples.get(0).time), span = time - start;
        if (span < INTERVAL || (Double.isFinite(published) && time - publishedAt < INTERVAL))
            return published;
        double integral = 0;
        for (int i = 0; i < samples.size() - 1; i++) {
            Sample a = samples.get(i), b = samples.get(i + 1);
            integral += a.speed * (b.time - Math.max(start, a.time));
        }
        published = integral / span;
        publishedAt = time;
        return published;
    }
}
