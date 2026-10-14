package com.runtracker.app;

import java.util.Locale;

public final class RunMath {
    public static String currentPace(double speedKmh) {
        if (!Double.isFinite(speedKmh) || speedKmh <= 0) return "—";
        double seconds = 3600 / speedKmh;
        if (!Double.isFinite(seconds) || seconds > Long.MAX_VALUE) return "—";
        long rounded = Math.round(seconds);
        return String.format(Locale.US, "%d:%02d", rounded / 60, rounded % 60);
    }

    public static double speedKmh(double metersPerSecond) {
        return Double.isFinite(metersPerSecond) && metersPerSecond >= 0 ? metersPerSecond * 3.6 : 0;
    }

    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }

    public static String pace(long ms, double m) {
        if (m < 10 || ms <= 0 || !Double.isFinite(m)) return "—";
        long s = Math.round(ms / m);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    public static double worldX(double lon) {
        return (lon + 180) / 360;
    }

    public static double worldY(double lat) {
        double r = Math.toRadians(Math.max(-85.05112878, Math.min(85.05112878, lat)));
        return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2;
    }

    public static double longitude(double x) {
        return x * 360 - 180;
    }

    public static double latitude(double y) {
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * y))));
    }
}
