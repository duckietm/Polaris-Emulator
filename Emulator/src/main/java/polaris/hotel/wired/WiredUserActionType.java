package polaris.hotel.wired;

/**
 * The actions "user performs action" boxes react to, with the official ids (AIR {@code WiredUserAction}):
 * wave 0, blow 1, laugh 2, respect 3, awake 4, sleep 5, sit 6, stand 7, lay 8, sign 10, dance 11.
 * Sign and dance carry an extra (the sign id 0..17, the dance 1..4), written {@code "N"} and
 * {@code "dance N"} in the string param.
 */
public final class WiredUserActionType {
    /** No action: not a valid choice, only the "nothing to report" value. */
    public static final int NONE = -1;

    public static final int WAVE = 0;
    public static final int BLOW_KISS = 1;
    public static final int LAUGH = 2;
    /** The thumbs-up gesture: the official "respect" action. */
    public static final int RESPECT = 3;

    public static final int THUMB_UP = RESPECT;
    public static final int AWAKE = 4;
    /** Idle ("sleep" in the official list). */
    public static final int RELAX = 5;

    public static final int SIT = 6;
    public static final int STAND = 7;
    public static final int LAY = 8;
    public static final int SIGN = 10;
    public static final int DANCE = 11;

    /** The highest sign id and dance id an extra may name. */
    public static final int MAX_SIGN = 17;

    public static final int MAX_DANCE = 4;

    private WiredUserActionType() {}

    public static boolean isAction(int action) {
        return action == WAVE
                || action == BLOW_KISS
                || action == LAUGH
                || action == RESPECT
                || action == AWAKE
                || action == RELAX
                || action == SIT
                || action == STAND
                || action == LAY
                || action == SIGN
                || action == DANCE;
    }

    /**
     * An action id saved before the official ids: wave 1, blow 2, laugh 3, awake 4, relax 5, sit 6,
     * stand 7, lay 8, sign 9, dance 10, thumb up 11.
     */
    public static int fromLegacy(int legacy) {
        return switch (legacy) {
            case 1 -> WAVE;
            case 2 -> BLOW_KISS;
            case 3 -> LAUGH;
            case 9 -> SIGN;
            case 10 -> DANCE;
            case 11 -> RESPECT;
            case 4, 5, 6, 7, 8 -> legacy;
            default -> WAVE;
        };
    }

    /** The official string param for an action's extra: the sign id, {@code "dance N"}, or empty. */
    public static String extraString(int action, boolean signFilter, int signId, boolean danceFilter, int danceId) {
        if (action == SIGN && signFilter) return String.valueOf(signId);
        if (action == DANCE && danceFilter) return "dance " + danceId;
        return "";
    }

    /** The extra id an official string param names for {@code action}, or -1 for none. */
    public static int extraCode(int action, String extra) {
        if (extra == null || extra.isBlank()) return -1;
        try {
            if (action == SIGN) {
                int sign = Integer.parseInt(extra.trim());
                return (sign >= 0 && sign <= MAX_SIGN) ? sign : -1;
            }
            if (action == DANCE) {
                String[] parts = extra.trim().split(" ");
                int dance = Integer.parseInt(parts[parts.length - 1]);
                return (dance >= 1 && dance <= MAX_DANCE) ? dance : -1;
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return -1;
    }
}
