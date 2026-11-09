package polaris.messages.incoming.housekeeping;

import polaris.database.SqlQueries;
import polaris.hotel.commands.UpdateWordFilterCommand;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.modtool.WordFilter;

/**
 * Adds a word to the word filter (or changes its replacement) and removes one, then reloads the
 * filter through :update_wordfilter so the change applies at once. Needs the :filterword
 * command's own permission on top of panel access. The list itself is the hotel.wordfilter
 * housekeeping list.
 */
public class HousekeepingWordFilterEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.HOTEL;
    }

    public static final String ADD = "add";
    public static final String REMOVE = "remove";

    public static final String ACTION_PREFIX = "hotel.wordfilter.";
    static final String PERMISSION = "cmd_filterword";

    /** The wordfilter columns: `key` varchar(256), `replacement` varchar(16). */
    public static final int MAX_WORD_LENGTH = 256;

    public static final int MAX_REPLACEMENT_LENGTH = 16;

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
        String word = this.packet.readString();
        String replacement = this.packet.readString();
        String actionKey = ACTION_PREFIX + action;

        if (!this.client.getUser().hasPermission(PERMISSION)) {
            this.reply(actionKey, HousekeepingResult.fail(HousekeepingAccess.DENIED_MESSAGE));
            return;
        }

        UpdateWordFilterCommand command = new UpdateWordFilterCommand();
        this.reply(
                actionKey,
                edit(
                        this.operator(),
                        action,
                        word,
                        replacement,
                        () -> command.handle(
                                this.client,
                                new String[] {command.keys.length > 0 ? command.keys[0] : "update_wordfilter"})));
    }

    /**
     * Adds a word (or changes its replacement; an empty replacement is the default one) or removes
     * one, then runs {@code reload} so the filter applies the change at once.
     */
    public static HousekeepingResult edit(
            HousekeepingOperator operator,
            String rawAction,
            String rawWord,
            String rawReplacement,
            HousekeepingOperator.Reload reload)
            throws Exception {
        String action = HousekeepingInputGuard.normalize(rawAction);
        String word = HousekeepingInputGuard.normalize(rawWord);
        String replacement = HousekeepingInputGuard.normalize(rawReplacement);
        String actionKey = ACTION_PREFIX + action;

        if (word.isEmpty() || !HousekeepingInputGuard.isWithinLimit(word, MAX_WORD_LENGTH)) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        if (!HousekeepingInputGuard.isWithinLimit(replacement, MAX_REPLACEMENT_LENGTH)) {
            return HousekeepingResult.fail("housekeeping.error.input_too_long");
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
            return HousekeepingResult.fail("housekeeping.error.wordfilter_failed");
        }

        if (changed < 0) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        // Re-adding a word unchanged can report 0 rows; only a removal that matched nothing is an error.
        if (changed == 0 && REMOVE.equals(action)) {
            return HousekeepingResult.fail("housekeeping.error.word_not_found");
        }

        reload.run();

        HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                actionKey,
                HousekeepingAuditLog.TARGET_HOTEL,
                0,
                "",
                "word=" + HousekeepingInputGuard.auditValue(word)
                        + (ADD.equals(action) ? " replacement=" + HousekeepingInputGuard.auditValue(replacement) : ""),
                operator.ip());
        return HousekeepingResult.ok(0);
    }
}
