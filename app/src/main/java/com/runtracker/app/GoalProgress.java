package com.runtracker.app;

import java.util.*;

public final class GoalProgress {
    private int completed;
    private boolean reached;

    public GoalProgress(int completed, boolean reached) {
        this.completed = Math.max(0, completed);
        this.reached = reached;
    }

    public static boolean validTarget(double km) {
        return Double.isFinite(km) && km >= .01 && km <= Float.MAX_VALUE;
    }

    public static double addTarget(double current, double increment) {
        if (!Double.isFinite(current)
                || current < 0
                || !Double.isFinite(increment)
                || increment <= 0) return Double.NaN;
        double result = current + increment;
        return validTarget(result) ? result : Double.NaN;
    }

    public static String number(double km) {
        String value = String.format(Locale.US, "%.2f", km);
        while (value.endsWith("0")) value = value.substring(0, value.length() - 1);
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }

    public List<String> advance(double meters, double target) {
        List<String> messages = new ArrayList<>();
        if (!Double.isFinite(meters) || meters < 0) return messages;
        int kms = (int) Math.floor(meters / 1000);
        while (completed < kms) {
            completed++;
            String message =
                    completed + (completed == 1 ? " kilometre done." : " kilometres done.");
            if (target > 0) {
                if (completed >= target) {
                    message +=
                            reached ? " You're past your target." : " Target reached. Well done!";
                    reached = true;
                } else {
                    double left = target - completed;
                    message +=
                            " "
                                    + number(left)
                                    + (left == 1 ? " kilometre" : " kilometres")
                                    + " to go.";
                }
            }
            messages.add(message);
        }
        if (target > 0 && meters >= target * 1000 && !reached) {
            reached = true;
            messages.add("Target reached. " + number(target) + " kilometres completed. Well done!");
        }
        return messages;
    }

    public int completed() {
        return completed;
    }

    public boolean reached() {
        return reached;
    }
}
