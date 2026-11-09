package polaris.messages.incoming.housekeeping;

/**
 * The outcome of a housekeeping action core: the panel turns it into a
 * HousekeepingActionResultComposer, RCON into its {status, message} reply.
 *
 * @param success whether the action went through
 * @param id      the action id the panel shows (the user, room or ban id, a count), 0 on failure
 * @param message the error key on failure; on success empty or a value for the operator (a password)
 */
public record HousekeepingResult(boolean success, int id, String message) {
    public HousekeepingResult {
        message = message == null ? "" : message;
    }

    public static HousekeepingResult ok(int id) {
        return new HousekeepingResult(true, id, "");
    }

    public static HousekeepingResult ok(int id, String message) {
        return new HousekeepingResult(true, id, message);
    }

    public static HousekeepingResult fail(String message) {
        return new HousekeepingResult(false, 0, message);
    }
}
