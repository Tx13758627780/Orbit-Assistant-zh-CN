package com.orbit.assistant;

import android.content.Context;

/** Resource access for shared presentation code that does not own a View or Activity.
 *
 * Only developer-authored interface text calls this helper. User messages, model responses,
 * preference keys and API payloads must never pass through a translation dictionary.
 */
public final class OrbitText {
    private static volatile Context application;

    private OrbitText() {}

    static void initialize(Context context) {
        application = context.getApplicationContext();
    }

    public static String get(int resourceId, String englishFallback) {
        Context context = application;
        // Plain JVM callers and very early class initialization keep the original English.
        // Read Resources each time: a configuration change must not cache the previous locale.
        return context == null ? englishFallback : context.getString(resourceId);
    }

    /** Resolve an explicitly identified array of built-in labels, never a user's content. */
    public static String[] labels(String[] originals, int... resourceIds) {
        if (originals.length != resourceIds.length) {
            throw new IllegalArgumentException("Label and resource counts differ");
        }
        String[] result = originals.clone();
        for (int i = 0; i < result.length; i++) {
            if (resourceIds[i] != 0) result[i] = get(resourceIds[i], originals[i]);
        }
        return result;
    }
}
