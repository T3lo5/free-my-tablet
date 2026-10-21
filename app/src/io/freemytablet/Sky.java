package io.freemytablet;

/**
 * The colors of the background through the day: night, dawn, day, sunset, dusk. The palette follows the sun
 * (the sunrise and sunset times from Sun), so the home screen is dark when you read at night and light by day.
 */
final class Sky {
    private Sky() {}

    /** Aurora colors: three tones (background, middle, highlight), from 0 to 1. */
    static final class Palette {
        final float[] c = new float[9];

        Palette set(int a, int b, int d) {
            put(0, a); put(3, b); put(6, d);
            return this;
        }

        private void put(int i, int rgb) {
            c[i] = ((rgb >> 16) & 255) / 255f;
            c[i + 1] = ((rgb >> 8) & 255) / 255f;
            c[i + 2] = (rgb & 255) / 255f;
        }

        int color(int i) {
            return 0xFF000000 | (Math.round(c[i * 3] * 255) << 16) | (Math.round(c[i * 3 + 1] * 255) << 8)
                    | Math.round(c[i * 3 + 2] * 255);
        }

        /** Luminance of the brightest tone (0 to 1): bright palettes (day, dawn) get darkened a little. */
        float brightest() {
            float max = 0;
            for (int i = 0; i < 9; i += 3) max = Math.max(max, 0.2126f * c[i] + 0.7152f * c[i + 1] + 0.0722f * c[i + 2]);
            return max;
        }
    }

    // Sky phases through the day.
    private static final int[] NIGHT = {0x070B1E, 0x16225A, 0x4B2F8F};
    private static final int[] DAWN = {0x24183F, 0xE89A55, 0xF6D59A};
    private static final int[] DAY = {0x1E57C8, 0x4FB9EC, 0x9EE2EE};
    private static final int[] SUNSET = {0x2E1650, 0xE2546C, 0xF39A4F};
    private static final int[] DUSK = {0x10173A, 0x36348A, 0x8A4BB0};

    /**
     * Palette for the sun's current position at your location. Uses the sunrise and sunset times from the last
     * fetch (they shift ~1 min a day, so yesterday's times are fine today); without data, 5:30 and 17:40.
     */
    static void sky(Palette out, long now, long sunrise, long sunset) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        long midnight = cal.getTimeInMillis();
        long rise = midnight + timeOfDay(sunrise, (5 * 60 + 30) * 60_000L);
        long set = midnight + timeOfDay(sunset, (17 * 60 + 40) * 60_000L);
        long m = 60_000L;
        long[] at = {rise - 60 * m, rise - 15 * m, rise + 45 * m, set - 45 * m, set, set + 45 * m, set + 105 * m};
        int[][] key = {NIGHT, DAWN, DAY, DAY, SUNSET, DUSK, NIGHT};
        if (now <= at[0] || now >= at[at.length - 1]) {
            out.set(NIGHT[0], NIGHT[1], NIGHT[2]);
            return;
        }
        for (int i = 0; i < at.length - 1; i++) {
            if (now >= at[i] && now < at[i + 1]) {
                float f = (float) (now - at[i]) / (at[i + 1] - at[i]);
                out.set(mix(key[i][0], key[i + 1][0], f), mix(key[i][1], key[i + 1][1], f), mix(key[i][2], key[i + 1][2], f));
                return;
            }
        }
    }

    /** Time of day (ms since local midnight) of an instant; `fallback` if there is no data. */
    static long timeOfDay(long at, long fallback) {
        if (at == 0) return fallback;
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(at);
        return ((c.get(java.util.Calendar.HOUR_OF_DAY) * 60L + c.get(java.util.Calendar.MINUTE)) * 60
                + c.get(java.util.Calendar.SECOND)) * 1000L;
    }

    private static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }
}
