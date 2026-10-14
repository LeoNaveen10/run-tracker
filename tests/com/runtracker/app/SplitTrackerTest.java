package com.runtracker.app;

import java.util.*;

public class SplitTrackerTest {
    private static int checks;

    public static void main(String[] args) {
        SplitTracker tracker = new SplitTracker(0, 0, 0, 0);
        check(tracker.advance(990, 297000).isEmpty(), "Incomplete kilometres are not fabricated");
        List<SplitTracker.Split> rows = tracker.advance(1010, 303000);
        check(
                rows.size() == 1 && rows.get(0).kilometre == 1,
                "First complete kilometre is recorded");
        check(
                rows.get(0).duration == 300000 && rows.get(0).elapsed == 300000,
                "Boundary interpolated between GPS samples");
        rows = tracker.advance(2100, 630000);
        check(
                rows.size() == 1 && rows.get(0).duration == 300000,
                "Second kilometre has its own duration");
        rows = tracker.advance(4100, 1230000);
        check(
                rows.size() == 2 && rows.get(0).kilometre == 3 && rows.get(1).kilometre == 4,
                "Every crossed boundary recorded");
        check(
                rows.get(0).duration == 300000 && rows.get(1).duration == 300000,
                "Multiple boundaries have distinct split intervals");
        check(tracker.advance(4100, 1240000).isEmpty(), "Standing still does not create a split");
        rows = tracker.advance(5000, 1510000);
        check(rows.get(0).duration == 310000, "Unpaused stationary time counts in the split");
        SplitTracker recovered = new SplitTracker(4, 1200000, 4100, 1230000);
        rows = recovered.advance(5000, 1500000);
        check(
                rows.size() == 1 && rows.get(0).kilometre == 5 && rows.get(0).duration == 300000,
                "Recovery preserves previous split boundary");
        check(
                recovered.advance(5000, 1500000).isEmpty(),
                "Duplicate distance does not repeat a split");
        SplitTracker old = new SplitTracker(2, -1, 2500, 750000);
        rows = old.advance(3000, 900000);
        check(
                rows.size() == 1 && rows.get(0).duration == -1,
                "Older runs do not get invented historical split times");
        check(
                old.advance(4000, 1200000).get(0).duration == 300000,
                "New complete split after legacy boundary is accurate");
        check(old.advance(3500, 1300000).isEmpty(), "Regressing distance ignored");
        check(old.advance(Double.NaN, 1300000).isEmpty(), "Invalid distance ignored");
        check(old.advance(4500, 1000000).isEmpty(), "Regressing elapsed time ignored");
        System.out.println(checks + " kilometre split checks passed");
    }

    private static void check(boolean pass, String message) {
        checks++;
        if (!pass) throw new AssertionError(message);
    }
}
