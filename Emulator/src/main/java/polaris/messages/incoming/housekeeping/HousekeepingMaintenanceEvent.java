package polaris.messages.incoming.housekeeping;

import polaris.hotel.MaintenanceCountdown;
import polaris.hotel.MaintenanceMode;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.messages.outgoing.housekeeping.HousekeepingMaintenanceStatusComposer;

/**
 * Maintenance from the panel: read its state, start it with a countdown (0 closes at once),
 * stop a running countdown, or switch maintenance off. Every answer ends with the current
 * state. Changing it needs the :maintenance command's own permission on top of panel access.
 */
public class HousekeepingMaintenanceEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.HOTEL;
    }

    public static final String STATUS = "status";
    public static final String START = "start";
    public static final String CANCEL = "cancel";
    public static final String DISABLE = "disable";

    public static final String ACTION_PREFIX = "hotel.maintenance.";
    static final String PERMISSION = "cmd_maintenance";

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        String action = HousekeepingInputGuard.normalize(this.packet.readString());
        String message = this.packet.readString();
        int minutes = this.packet.readInt();

        if (!STATUS.equals(action)) {
            HousekeepingResult result = this.client.getUser().hasPermission(PERMISSION)
                    ? apply(this.operator(), action, message, minutes)
                    : HousekeepingResult.fail(HousekeepingAccess.DENIED_MESSAGE);

            this.reply(ACTION_PREFIX + action, result);
        }

        this.client.sendResponse(new HousekeepingMaintenanceStatusComposer(
                MaintenanceMode.isEnabled(),
                MaintenanceMode.getMinRank(),
                MaintenanceMode.getMessage(),
                MaintenanceCountdown.getEndsAt()));
    }

    /**
     * Applies a change ({@link #START}, {@link #CANCEL} or {@link #DISABLE}) and audits it. A start
     * closes the hotel after {@code minutes} (0 to {@link MaintenanceCountdown#MAX_MINUTES}; 0 closes
     * at once) with the message, or the default one when it is empty.
     */
    public static HousekeepingResult apply(
            HousekeepingOperator operator, String rawAction, String rawMessage, int minutes) {
        String action = HousekeepingInputGuard.normalize(rawAction);
        String message = HousekeepingInputGuard.normalize(rawMessage);
        String error = change(action, message, minutes);

        if (error != null) {
            return HousekeepingResult.fail(error);
        }

        HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_PREFIX + action,
                HousekeepingAuditLog.TARGET_HOTEL,
                0,
                "",
                START.equals(action)
                        ? "minutes=" + minutes + " message=" + HousekeepingInputGuard.auditValue(message)
                        : "",
                operator.ip());
        return HousekeepingResult.ok(0);
    }

    /** Applies a change and returns the error key, or null when it went through. */
    private static String change(String action, String message, int minutes) {
        return switch (action) {
            case START -> {
                if (minutes < 0 || minutes > MaintenanceCountdown.MAX_MINUTES) {
                    yield "housekeeping.error.invalid_input";
                }

                if (!HousekeepingInputGuard.isWithinLimit(message, MaintenanceMode.MAX_MESSAGE_LENGTH)) {
                    yield "housekeeping.error.input_too_long";
                }

                MaintenanceCountdown.start(minutes, message.isEmpty() ? null : message);
                yield null;
            }
            case CANCEL -> MaintenanceCountdown.cancel() ? null : "housekeeping.error.no_countdown";
            case DISABLE -> {
                MaintenanceCountdown.cancel();
                MaintenanceMode.setEnabled(false, null);
                yield null;
            }
            default -> "housekeeping.error.invalid_input";
        };
    }
}
