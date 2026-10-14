package com.runtracker.app;

public final class RollingPaceTest {
    private static int checks;

    private static void near(double actual, double expected) {
        checks++;
        if (Math.abs(actual - expected) > 1e-9 || !Double.isFinite(actual))
            throw new AssertionError(actual + " != " + expected);
    }

    private static void unavailable(double actual) {
        checks++;
        if (!Double.isNaN(actual)) throw new AssertionError("Expected warmup");
    }

    public static void main(String[] args) {
        RollingPace pace = new RollingPace();
        for (int i = 0; i < 5; i++) unavailable(pace.update(i * 1000, 2));
        near(pace.update(5000, 2), 2);
        for (int i = 6; i < 10; i++) near(pace.update(i * 1000, 4), 2);
        near(pace.update(10000, 4), 3);
        for (int i = 11; i <= 15; i++) pace.update(i * 1000, 4);
        near(pace.update(16000, 4), 4);
        unavailable(pace.update(22000, 3));
        pace.reset();
        unavailable(pace.update(0, 2));
        pace.update(2000, 4);
        near(pace.update(5000, 4), 3.2);
        near(pace.update(4000, 100), 3.2);
        near(pace.update(6000, Double.NaN), 3.2);
        pace.reset();
        for (int i = 0; i <= 8; i++) pace.update(i * 1000, 0);
        near(pace.update(10000, 0), 0);
        System.out.println("RollingPaceTest: " + checks + " checks passed");
    }
}
