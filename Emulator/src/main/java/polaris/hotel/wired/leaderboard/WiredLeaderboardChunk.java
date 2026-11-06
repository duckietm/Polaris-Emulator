package polaris.hotel.wired.leaderboard;

import java.util.List;

/** Rows {@code [index * 50, index * 50 + 50)} of a leaderboard, and how many rows it has in total. */
public record WiredLeaderboardChunk(int index, int totalCount, List<WiredLeaderboardRow> rows) {
    public static final int SIZE = 50;

    public WiredLeaderboardChunk {
        rows = List.copyOf(rows);
    }
}
