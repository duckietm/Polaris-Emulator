package polaris.hotel.games.snowwar;

/**
 * AIR GameLobbyPlayerData.skillLevel (1..30, drawn as ten tiered stars in the
 * lobby and on the results screen), derived from the all-time SnowStorm score.
 *
 * <p>Level {@code n} is reached at {@code 50 * (n - 1)^2} points: 50 for
 * level 2, 200 for level 3, ... 42,050 for level 30. Nothing official
 * documents the curve, so it only has to be monotonic and reachable.
 */
public final class SnowWarSkillLevel {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 30;
    static final int POINTS_PER_STEP = 50;

    private SnowWarSkillLevel() {}

    public static int fromTotalScore(int totalScore) {
        if (totalScore <= 0) {
            return MIN_LEVEL;
        }
        int level = MIN_LEVEL + (int) Math.floor(Math.sqrt(totalScore / (double) POINTS_PER_STEP));
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    /** Total score needed to reach the given level. */
    public static int scoreForLevel(int level) {
        int clamped = Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
        return POINTS_PER_STEP * (clamped - 1) * (clamped - 1);
    }

    /** Points still missing to the next level (0 at the cap). */
    public static int scoreToNextLevel(int totalScore) {
        int level = fromTotalScore(totalScore);
        if (level >= MAX_LEVEL) {
            return 0;
        }
        return Math.max(0, scoreForLevel(level + 1) - Math.max(0, totalScore));
    }
}
