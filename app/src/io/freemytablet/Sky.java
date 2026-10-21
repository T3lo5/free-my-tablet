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

    // Sky phases through the day. Deeper than the TV's: on a small screen the white text needs the contrast,
    // and a tablet that is read on deserves a calm sky, not a bright one.
    private static final int[] NIGHT = {0x05081A, 0x101B45, 0x3E277A};
    private static final int[] DAWN = {0x1E1436, 0xD98A4C, 0xEFCB8E};
    private static final int[] DAY = {0x0E2E6B, 0x1F5FA8, 0x5FA8D4};
    private static final int[] SUNSET = {0x2A1145, 0xC24357, 0xE8894A};
    private static final int[] DUSK = {0x0C1230, 0x2B2A70, 0x6E3C90};

    /**
     * Palette for the sun's current position at your location. Uses the sunrise and sunset times from the last
     * fetch (they shift ~1 min a day, so yesterday's times are fine today); without data, 5:30 and 17:40.
     */
    static void sky(Palette out, long now, long sunrise, long sunset) {        java.util.Calendar cal = java.util.Calendar.getInstance();
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

    /** The day palette, whatever the hour (the "automatic" theme). */
    static void light(Palette out) {
        out.set(DAY[0], DAY[1], DAY[2]);
    }

    /** The "light" theme of the settings: warm paper, for reading. */
    static void paper(Palette out) {
        out.set(0xE8E2D6, 0xF3EEE4, 0xFFFBF2);
    }

    /** The night palette, whatever the hour (the "dark" theme). */
    static void dark(Palette out) {
        out.set(NIGHT[0], NIGHT[1], NIGHT[2]);
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
