package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.habbohotel.MaintenanceCountdown;
import com.eu.habbo.habbohotel.MaintenanceMode;
import com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingMaintenanceStatusComposer;

/**
 * Maintenance from the panel: read its state, start it with a countdown (0 closes at once),
 * stop a running countdown, or switch maintenance off. Every answer ends with the current
 * state. Changing it needs the :maintenance command's own permission on top of panel access.
 */
public class HousekeepingMaintenanceEvent extends HousekeepingHandler {
    static final String STATUS = "status";
    static final String START = "start";
    static final String CANCEL = "cancel";
    static final String DISABLE = "disable";

    static final String ACTION_PREFIX = "hotel.maintenance.";
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
        String message = HousekeepingInputGuard.normalize(this.packet.readString());
        int minutes = this.packet.readInt();
        String actionKey = ACTION_PREFIX + action;

        if (!STATUS.equals(action)) {
            String error = this.apply(action, message, minutes);

            if (error != null) {
                this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, false, 0, error));
            } else {
                HousekeepingAuditLog.log(
                        this.client.getHabbo().getHabboInfo().getId(),
                        this.client.getHabbo().getHabboInfo().getUsername(),
                        actionKey,
                        HousekeepingAuditLog.TARGET_HOTEL,
                        0,
                        "",
                        START.equals(action)
                                ? "minutes=" + minutes + " message=" + HousekeepingInputGuard.auditValue(message)
                                : "",
                        this.client.getHabbo().getHabboInfo().getIpLogin());
                this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, true, 0, ""));
            }
        }

        this.client.sendResponse(new HousekeepingMaintenanceStatusComposer(
                MaintenanceMode.isEnabled(),
                MaintenanceMode.getMinRank(),
                MaintenanceMode.getMessage(),
                MaintenanceCountdown.getEndsAt()));
    }

    /** Applies a change and returns the error key, or null when it went through. */
    private String apply(String action, String message, int minutes) {
        if (!this.client.getHabbo().hasPermission(PERMISSION)) {
            return HousekeepingAccess.DENIED_MESSAGE;
        }

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
