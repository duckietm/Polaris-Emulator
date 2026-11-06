package polaris.hotel.wired.protocol;

/**
 * The official variable targets on the wire ({@code WiredVariableTargetType}: furni 0, user 1, global
 * -10, context -20) against the holder kinds our variable conditions store (user 0, furni 1, context 2,
 * room 3). Stored boxes keep our kinds; only the editor packets use the official numbers.
 */
public final class WiredVariableTargets {
    public static final int OFFICIAL_FURNI = 0;
    public static final int OFFICIAL_USER = 1;
    public static final int OFFICIAL_GLOBAL = -10;
    public static final int OFFICIAL_CONTEXT = -20;

    private WiredVariableTargets() {}

    /** The official target of our holder kind. */
    public static int toOfficial(int ours) {
        return switch (ours) {
            case 1 -> OFFICIAL_FURNI;
            case 2 -> OFFICIAL_CONTEXT;
            case 3 -> OFFICIAL_GLOBAL;
            default -> OFFICIAL_USER;
        };
    }

    // Our value comparisons (0 >, 1 >=, 2 =, 3 <=, 4 <, 5 !=) against the official numbers
    // (0 <, 1 =, 2 >, 3 <=, 4 !=, 5 >=).
    private static final int[] OFFICIAL_COMPARISON = {2, 5, 1, 3, 0, 4};
    private static final int[] OUR_COMPARISON = {4, 2, 0, 3, 5, 1};

    /** Our value comparison as the official number (equals for anything unknown). */
    public static int comparisonToOfficial(int ours) {
        return ours >= 0 && ours < OFFICIAL_COMPARISON.length ? OFFICIAL_COMPARISON[ours] : 1;
    }

    /** The official comparison number as ours (equals for anything unknown). */
    public static int comparisonFromOfficial(int official) {
        return official >= 0 && official < OUR_COMPARISON.length ? OUR_COMPARISON[official] : 2;
    }

    /** Our holder kind of an official target; anything unknown is a user. */
    public static int fromOfficial(int official) {
        return switch (official) {
            case OFFICIAL_FURNI -> 1;
            case OFFICIAL_CONTEXT -> 2;
            case OFFICIAL_GLOBAL -> 3;
            default -> 0;
        };
    }
}
