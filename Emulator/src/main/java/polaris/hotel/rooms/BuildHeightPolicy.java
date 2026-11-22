package polaris.hotel.rooms;

import polaris.Emulator;
import polaris.hotel.users.User;

/**
 * Who may use the build height widget, and the range its slider covers.
 *
 * <p>Both used to come from the BuildHeight plugin, which the widget in core replaced: the rank
 * permission {@value #PERMISSION} and the two settings {@value #MIN_KEY} / {@value #MAX_KEY}, under
 * the names the plugin gave them, so a hotel that ran the plugin keeps what it had set. A hotel that
 * never did gets every rank allowed and the range the widget always had, the floor up to the
 * furniture ceiling.
 */
public final class BuildHeightPolicy {
    public static final String PERMISSION = "cmd_buildheight";
    public static final String MIN_KEY = "building.cmd_buildheight.min_height";
    public static final String MAX_KEY = "building.cmd_buildheight.max_height";

    private BuildHeightPolicy() {}

    /** Build rights in the room, and a rank that is allowed the widget at all. */
    public static boolean mayUse(User user, boolean hasRights) {
        return hasRights && user != null && user.hasPermission(PERMISSION, true);
    }

    /** The lowest height the slider reaches: the floor (0) unless the hotel allows building below it. */
    public static int minHeight() {
        return minHeight(setting(MIN_KEY), Room.MAXIMUM_FURNI_HEIGHT);
    }

    /** The highest height the slider reaches: the furniture ceiling unless the hotel sets it lower. */
    public static int maxHeight() {
        return maxHeight(setting(MAX_KEY), Room.MAXIMUM_FURNI_HEIGHT);
    }

    /** Never above the floor, and never further below it than the ceiling is above it. */
    static int minHeight(String configured, double ceiling) {
        int limit = (int) Math.floor(ceiling);
        return Math.max(-limit, Math.min(0, parse(configured, 0)));
    }

    /** Never below the floor and never above the furniture ceiling. */
    static int maxHeight(String configured, double ceiling) {
        int limit = (int) Math.floor(ceiling);
        return Math.max(0, Math.min(limit, parse(configured, limit)));
    }

    /** A height the client asked for, kept inside the range. */
    public static double clamp(double height, int min, int max) {
        if (Double.isNaN(height)) return 0.0D;
        return Math.max(min, Math.min(height, max));
    }

    private static String setting(String key) {
        // Not loaded yet (and in tests): the range the widget always had.
        var config = Emulator.getConfig();
        return config == null ? "" : config.getValue(key, "");
    }

    private static int parse(String configured, int fallback) {
        if (configured == null || configured.isBlank()) return fallback;
        try {
            return Integer.parseInt(configured.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
