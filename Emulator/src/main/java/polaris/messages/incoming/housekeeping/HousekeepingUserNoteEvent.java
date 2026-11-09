package polaris.messages.incoming.housekeeping;

import polaris.database.SqlQueries;
import polaris.hotel.modtool.HousekeepingAuditLog;

/**
 * Adds an internal staff note to a user, or deletes one of the operator's own notes. The list
 * is the user.notes housekeeping list. The work is {@link #edit}, shared with the RCON
 * housekeeping command.
 */
public class HousekeepingUserNoteEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.USERS;
    }

    public static final String ADD = "add";
    public static final String DELETE = "delete";
    public static final String ACTION_PREFIX = "user.note.";

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        String action = HousekeepingInputGuard.normalize(this.packet.readString());
        int userId = this.packet.readInt();
        int noteId = this.packet.readInt();
        String note = this.packet.readString();

        this.reply(ACTION_PREFIX + action, edit(this.operator(), action, userId, noteId, note));
    }

    /**
     * Adds a note ({@link #ADD}, needs {@code note}) or deletes one ({@link #DELETE}, needs
     * {@code noteId}). A staff member deletes only their own notes; the system operator, which
     * writes no notes of its own, deletes any note of the user.
     */
    public static HousekeepingResult edit(
            HousekeepingOperator operator, String rawAction, int userId, int noteId, String rawNote) {
        String action = HousekeepingInputGuard.normalize(rawAction);
        String note = HousekeepingInputGuard.normalize(rawNote);
        String actionKey = ACTION_PREFIX + action;

        if (userId <= 0 || !HousekeepingMutationGuard.userExists(userId)) {
            return HousekeepingResult.fail("housekeeping.error.user_not_found");
        }

        int changed;

        try {
            switch (action) {
                case ADD -> {
                    if (note.isEmpty()) {
                        return HousekeepingResult.fail("housekeeping.error.invalid_input");
                    }

                    if (!HousekeepingInputGuard.isWithinLimit(note, HousekeepingUserNotes.MAX_NOTE_LENGTH)) {
                        return HousekeepingResult.fail("housekeeping.error.input_too_long");
                    }

                    changed = HousekeepingUserNotes.add(
                            userId, operator.id(), operator.name(), note, (int) (System.currentTimeMillis() / 1000L));
                }
                case DELETE ->
                    changed = operator.isSystem()
                            ? HousekeepingUserNotes.deleteAny(noteId, userId)
                            : HousekeepingUserNotes.delete(noteId, userId, operator.id());
                default -> {
                    return HousekeepingResult.fail("housekeeping.error.invalid_input");
                }
            }
        } catch (SqlQueries.DataAccessException e) {
            return HousekeepingResult.fail("housekeeping.error.note_failed");
        }

        if (changed <= 0) {
            if (!DELETE.equals(action)) {
                return HousekeepingResult.fail("housekeeping.error.note_failed");
            }

            return HousekeepingResult.fail(
                    operator.isSystem() ? "housekeeping.error.note_not_found" : "housekeeping.error.note_not_yours");
        }

        HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                actionKey,
                HousekeepingAuditLog.TARGET_USER,
                userId,
                "",
                // The note's text stays in the notes: the log is read by more operators than the notes are.
                ADD.equals(action) ? "note_length=" + note.length() : "note_id=" + noteId,
                operator.ip());
        return HousekeepingResult.ok(0);
    }
}
