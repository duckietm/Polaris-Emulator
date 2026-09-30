package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.database.SqlQueries;
import com.eu.habbo.habbohotel.commands.UpdateWordFilterCommand;
import com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Adds a word to the word filter (or changes its replacement) and removes one, then reloads the
 * filter through :update_wordfilter so the change applies at once. Needs the :filterword
 * command's own permission on top of panel access. The list itself is the hotel.wordfilter
 * housekeeping list.
 */
public class HousekeepingWordFilterEvent extends HousekeepingHandler {
    static final String ADD = "add";
    static final String REMOVE = "remove";

    static final String ACTION_PREFIX = "hotel.wordfilter.";
    static final String PERMISSION = "cmd_filterword";

    /** The wordfilter columns: `key` varchar(256), `replacement` varchar(16). */
    static final int MAX_WORD_LENGTH = 256;

    static final int MAX_REPLACEMENT_LENGTH = 16;

    static final String ADD_SQL = "INSERT INTO wordfilter (`key`, `replacement`) VALUES (?, ?) "
            + "ON DUPLICATE KEY UPDATE `replacement` = VALUES(`replacement`)";
    static final String REMOVE_SQL = "DELETE FROM wordfilter WHERE `key` = ?";

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
        String word = HousekeepingInputGuard.normalize(this.packet.readString());
        String replacement = HousekeepingInputGuard.normalize(this.packet.readString());
        String actionKey = ACTION_PREFIX + action;

        if (!this.client.getHabbo().hasPermission(PERMISSION)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, HousekeepingAccess.DENIED_MESSAGE));
            return;
        }

        if (word.isEmpty() || !HousekeepingInputGuard.isWithinLimit(word, MAX_WORD_LENGTH)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingInputGuard.isWithinLimit(replacement, MAX_REPLACEMENT_LENGTH)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.input_too_long"));
            return;
        }

        if (replacement.isEmpty()) replacement = WordFilter.DEFAULT_REPLACEMENT;

        int changed;

        try {
            changed = switch (action) {
                case ADD -> SqlQueries.update(ADD_SQL, word, replacement);
                case REMOVE -> SqlQueries.update(REMOVE_SQL, word);
                default -> -1;
            };
        } catch (SqlQueries.DataAccessException e) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.wordfilter_failed"));
            return;
        }

        if (changed < 0) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        // Re-adding a word unchanged can report 0 rows; only a removal that matched nothing is an error.
        if (changed == 0 && REMOVE.equals(action)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.word_not_found"));
            return;
        }

        UpdateWordFilterCommand reload = new UpdateWordFilterCommand();
        reload.handle(this.client, new String[] {reload.keys.length > 0 ? reload.keys[0] : "update_wordfilter"});

        HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                actionKey,
                HousekeepingAuditLog.TARGET_HOTEL,
                0,
                "",
                "word=" + HousekeepingInputGuard.auditValue(word)
                        + (ADD.equals(action) ? " replacement=" + HousekeepingInputGuard.auditValue(replacement) : ""),
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, true, 0, ""));
    }
}
