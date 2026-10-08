package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingListComposer;
import java.util.List;

/** Internal staff notes on a user (housekeeping_user_notes): the list, adding one, deleting one's own. */
final class HousekeepingUserNotes {
    static final int MAX_NOTE_LENGTH = 500;
    static final int LIST_LIMIT = 100;

    static final String LIST_SQL = "SELECT id, note, author_id, author_name, created_at FROM housekeeping_user_notes "
            + "WHERE user_id = ? ORDER BY id DESC LIMIT ?";
    static final String ADD_SQL =
            "INSERT INTO housekeeping_user_notes (user_id, author_id, author_name, note, created_at) VALUES (?, ?, ?, ?, ?)";
    /** Only the author removes a note. */
    static final String DELETE_SQL =
            "DELETE FROM housekeeping_user_notes WHERE id = ? AND user_id = ? AND author_id = ?";

    private HousekeepingUserNotes() {}

    static HousekeepingListComposer list(String listKey, int userId) {
        try {
            List<List<String>> rows = SqlQueries.query(
                    LIST_SQL,
                    set -> List.of(
                            String.valueOf(set.getInt("id")),
                            set.getString("note"),
                            set.getString("author_name"),
                            String.valueOf(set.getInt("author_id")),
                            String.valueOf(set.getInt("created_at"))),
                    userId,
                    LIST_LIMIT);

            return new HousekeepingListComposer(
                    listKey, userId, true, "", List.of("note_id", "note", "staff", "staff_id", "time"), rows);
        } catch (SqlQueries.DataAccessException e) {
            return HousekeepingListComposer.failure(listKey, userId, "housekeeping.list.failed");
        }
    }

    static int add(int userId, int authorId, String authorName, String note, int now) {
        return SqlQueries.update(ADD_SQL, userId, authorId, authorName, note, now);
    }

    static int delete(int noteId, int userId, int authorId) {
        return SqlQueries.update(DELETE_SQL, noteId, userId, authorId);
    }
}
