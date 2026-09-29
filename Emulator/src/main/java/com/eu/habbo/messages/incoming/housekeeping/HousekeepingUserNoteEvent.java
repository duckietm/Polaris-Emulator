package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Adds an internal staff note to a user, or deletes one of the operator's own notes. The list
 * is the user.notes housekeeping list.
 */
public class HousekeepingUserNoteEvent extends MessageHandler {
    static final String ADD = "add";
    static final String DELETE = "delete";
    static final String ACTION_PREFIX = "user.note.";

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        if (!HousekeepingAccess.check(this.client)) {
            return;
        }

        String action = HousekeepingInputGuard.normalize(this.packet.readString());
        int userId = this.packet.readInt();
        int noteId = this.packet.readInt();
        String note = HousekeepingInputGuard.normalize(this.packet.readString());
        String actionKey = ACTION_PREFIX + action;
        int operatorId = this.client.getHabbo().getHabboInfo().getId();
        String operatorName = this.client.getHabbo().getHabboInfo().getUsername();

        if (userId <= 0 || !HousekeepingMutationGuard.userExists(userId)) {
            this.fail(actionKey, "housekeeping.error.user_not_found");
            return;
        }

        int changed;

        try {
            switch (action) {
                case ADD -> {
                    if (note.isEmpty()) {
                        this.fail(actionKey, "housekeeping.error.invalid_input");
                        return;
                    }

                    if (!HousekeepingInputGuard.isWithinLimit(note, HousekeepingUserNotes.MAX_NOTE_LENGTH)) {
                        this.fail(actionKey, "housekeeping.error.input_too_long");
                        return;
                    }

                    changed = HousekeepingUserNotes.add(
                            userId, operatorId, operatorName, note, (int) (System.currentTimeMillis() / 1000L));
                }
                case DELETE -> changed = HousekeepingUserNotes.delete(noteId, userId, operatorId);
                default -> {
                    this.fail(actionKey, "housekeeping.error.invalid_input");
                    return;
                }
            }
        } catch (SqlQueries.DataAccessException e) {
            this.fail(actionKey, "housekeeping.error.note_failed");
            return;
        }

        if (changed <= 0) {
            this.fail(
                    actionKey,
                    DELETE.equals(action) ? "housekeeping.error.note_not_yours" : "housekeeping.error.note_failed");
            return;
        }

        HousekeepingAuditLog.log(
                operatorId,
                operatorName,
                actionKey,
                HousekeepingAuditLog.TARGET_USER,
                userId,
                "",
                ADD.equals(action) ? "note=" + HousekeepingInputGuard.auditValue(note) : "note_id=" + noteId,
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, true, 0, ""));
    }

    private void fail(String actionKey, String message) {
        this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, false, 0, message));
    }
}
