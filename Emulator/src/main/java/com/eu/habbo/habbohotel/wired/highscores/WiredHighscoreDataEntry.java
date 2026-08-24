package com.eu.habbo.habbohotel.wired.highscores;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredHighscoreDataEntry {
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

        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Integer.valueOf(trimmed));
            } catch (NumberFormatException ignored) {
                // skip the malformed id rather than poisoning the whole load
            }
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
