package polaris.hotel.wired.leaderboard;

/**
 * One ranked user in a chunk. {@code rawValue} is the value as text, "" for a variable without one;
 * times are epoch milliseconds. {@code level} and {@code text} are null when not available.
 */
public record WiredLeaderboardRow(
        int rank,
        int userId,
        String username,
        String figure,
        String rawValue,
        long creationTime,
        long lastUpdateTime,
        Integer level,
        String text) {}
