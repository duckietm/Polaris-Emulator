package com.eu.habbo.habbohotel.wired.highscores;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WiredHighscoreDataEntry {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredHighscoreDataEntry.class);

    private final int itemId;
    private final List<Integer> userIds;
    private final int score;
    private final boolean isWin;
    private final int timestamp;

    public WiredHighscoreDataEntry(int itemId, List<Integer> userIds, int score, boolean isWin, int timestamp) {
        this.itemId = itemId;
        this.userIds = userIds;
        this.score = score;
        this.isWin = isWin;
        this.timestamp = timestamp;
    }

    public WiredHighscoreDataEntry(ResultSet set) throws SQLException {
        this.itemId = set.getInt("item_id");
        this.userIds = parseUserIds(set.getString("user_ids"));
        this.score = set.getInt("score");
        this.isWin = set.getInt("is_win") == 1;
        this.timestamp = set.getInt("timestamp");
    }

    /**
     * Parses the comma-separated user_ids column defensively: a NULL/empty
     * column or a non-numeric token must not throw (NPE / NumberFormatException)
     * out of the load loop, which would abort loading EVERY highscore row.
     */
    private static List<Integer> parseUserIds(String raw) {
        List<Integer> ids = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return ids;
        }

        int skipped = 0;
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Integer.valueOf(trimmed));
            } catch (NumberFormatException malformed) {
                // Skip the malformed id rather than poisoning the whole load, but
                // count it so the row is not discarded in complete silence.
                skipped++;
            }
        }

        if (skipped > 0) {
            // Bounded and redacted on purpose: one line per row, counts only, so a
            // corrupted column cannot flood the log or echo its contents back.
            LOGGER.debug("Skipped {} malformed highscore user id(s) while loading a wired highscore row", skipped);
        }

        return ids;
    }

    public int getItemId() {
        return itemId;
    }

    public List<Integer> getUserIds() {
        return userIds;
    }

    public int getScore() {
        return score;
    }

    public boolean isWin() {
        return isWin;
    }

    public int getTimestamp() {
        return timestamp;
    }
}
