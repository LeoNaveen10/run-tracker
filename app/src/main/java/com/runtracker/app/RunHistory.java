package com.runtracker.app;

import org.json.*;

/** Deletes one unchanged saved record, preserving any runs appended since it was opened. */
public final class RunHistory {
    public static boolean remove(JSONArray current, JSONObject selected) {
        String snapshot = selected.toString();
        for (int i = 0; i < current.length(); i++) {
            JSONObject run = current.optJSONObject(i);
            if (run != null && snapshot.equals(run.toString())) {
                current.remove(i);
                return true;
            }
        }
        return false;
    }
}
