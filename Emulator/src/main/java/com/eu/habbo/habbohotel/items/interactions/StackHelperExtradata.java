package com.eu.habbo.habbohotel.items.interactions;

/**
 * How a stack helper stores its state in {@code items.extra_data}.
 *
 * <p>The wire format the client reads is unchanged: a stack helper's stuff data
 * is the legacy string {@code "<height * 100>"}. The multi-walk checkbox of the
 * official {@code CustomStackHeightWidget} (AIR 13) travels appended to the
 * {@code SetStackHelperHeight} composer, and it has to survive a room reload,
 * so it is kept behind a {@code ";1"} suffix that never reaches the client.
 */
public final class StackHelperExtradata {

    private static final char SEPARATOR = ';';

    private StackHelperExtradata() {}

    /** The part the client is allowed to see: the height in hundredths. */
    public static String legacy(String extradata) {
        if (extradata == null) {
            return "";
        }
        int separator = extradata.indexOf(SEPARATOR);
        return separator < 0 ? extradata : extradata.substring(0, separator);
    }

    /** The stored height in tiles, or {@code 0} when the value is missing or malformed. */
    public static double height(String extradata) {
        String legacy = legacy(extradata);
        if (legacy.isEmpty()) {
            return 0.0D;
        }
        try {
            return Double.parseDouble(legacy) / 100.0D;
        } catch (NumberFormatException malformed) {
            return 0.0D;
        }
    }

    /** Whether the owner ticked "multi-walk" for this helper. */
    public static boolean multiWalk(String extradata) {
        if (extradata == null) {
            return false;
        }
        int separator = extradata.indexOf(SEPARATOR);
        return separator >= 0 && "1".equals(extradata.substring(separator + 1));
    }

    /**
     * The {@code furniture_extra} value a walk-magic tile carries, which is how
     * the official widget restores its checkbox
     * ({@code class_3611}: {@code getModel().getNumber("furniture_extra") == 1}).
     */
    public static int multiWalkExtra(String extradata) {
        return multiWalk(extradata) ? 1 : 0;
    }

    /** Rewrites the height, keeping whatever multi-walk state is already stored. */
    public static String write(String extradata, double height) {
        return compose((int) Math.round(height * 100.0D), multiWalk(extradata));
    }

    /** Rewrites both the height and the multi-walk state. */
    public static String write(double height, boolean multiWalk) {
        return compose((int) Math.round(height * 100.0D), multiWalk);
    }

    private static String compose(int hundredths, boolean multiWalk) {
        return multiWalk ? hundredths + String.valueOf(SEPARATOR) + "1" : Integer.toString(hundredths);
    }
}
