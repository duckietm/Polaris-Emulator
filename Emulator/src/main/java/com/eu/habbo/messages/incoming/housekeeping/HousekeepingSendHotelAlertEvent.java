package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.StaffAlertWithLinkComposer;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Mirrors :ha - staff alert with sender attribution. By default it goes to
 * every online user whose `blockStaffAlerts` flag isn't set; an optional
 * recipient narrows it to one online user (by name), the users in one active
 * room (by id), or the staff (holders of the staff alert permission). A
 * client that sends only the message keeps the hotel-wide broadcast. The
 * alert is composed once and forwarded by reference, so a broadcast is
 * O(N habbos) wire writes, not O(N) compose calls.
 */
public class HousekeepingSendHotelAlertEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.HOTEL;
    }

    private static final String ACTION_KEY = "hotel.alert";

    static final String SCOPE_HOTEL = "hotel";
    static final String SCOPE_USER = "user";
    static final String SCOPE_ROOM = "room";
    static final String SCOPE_STAFF = "staff";

    static final String STAFF_PERMISSION = "cmd_staffalert";

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        String message = HousekeepingInputGuard.normalize(this.packet.readString());
        String recipient = SCOPE_HOTEL;

        if (this.packet.bytesAvailable() > 0) {
            recipient = this.packet.readString();
        }

        String[] parsedRecipient = parseRecipient(recipient);
        String scope = parsedRecipient[0];
        String target = parsedRecipient[1];

        if (message.isEmpty()) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.alert_empty"));
            return;
        }

        if (!HousekeepingInputGuard.isWithinLimit(message, HousekeepingInputGuard.MAX_ALERT_LENGTH)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.input_too_long"));
            return;
        }

        GameEnvironment environment = Emulator.getGameEnvironment();
        List<Habbo> recipients = new ArrayList<>();
        String targetType = HousekeepingAuditLog.TARGET_HOTEL;
        int targetId = 0;
        String targetLabel = "";

        switch (scope) {
            case SCOPE_HOTEL -> {
                for (Habbo habbo :
                        environment.getHabboManager().getOnlineHabbos().values()) {
                    if (habbo != null && (habbo.getHabboStats() == null || !habbo.getHabboStats().blockStaffAlerts)) {
                        recipients.add(habbo);
                    }
                }
            }
            case SCOPE_USER -> {
                Habbo habbo =
                        target.isEmpty() ? null : environment.getHabboManager().getHabbo(target);

                if (habbo == null) {
                    this.client.sendResponse(new HousekeepingActionResultComposer(
                            ACTION_KEY, false, 0, "housekeeping.error.user_offline"));
                    return;
                }

                recipients.add(habbo);
                targetType = HousekeepingAuditLog.TARGET_USER;
                targetId = habbo.getHabboInfo().getId();
                targetLabel = habbo.getHabboInfo().getUsername();
            }
            case SCOPE_ROOM -> {
                Room room = environment.getRoomManager().getRoom(parseRoomId(target));

                if (room == null) {
                    this.client.sendResponse(new HousekeepingActionResultComposer(
                            ACTION_KEY, false, 0, "housekeeping.error.room_not_active"));
                    return;
                }

                recipients.addAll(room.getHabbos());
                targetType = HousekeepingAuditLog.TARGET_ROOM;
                targetId = room.getId();
                targetLabel = room.getName();
            }
            case SCOPE_STAFF -> {
                Collection<Habbo> online =
                        environment.getHabboManager().getOnlineHabbos().values();

                for (Habbo habbo : online) {
                    if (habbo != null && habbo.hasPermission(STAFF_PERMISSION)) recipients.add(habbo);
                }
            }
            default -> {
                this.client.sendResponse(
                        new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
                return;
            }
        }

        String body = message + "\r\n-" + this.client.getHabbo().getHabboInfo().getUsername();
        ServerMessage broadcast = new StaffAlertWithLinkComposer(body, "").compose();

        int reached = 0;

        for (Habbo habbo : recipients) {
            if (habbo == null || habbo.getClient() == null) continue;

            habbo.getClient().sendResponse(broadcast);
            reached++;
        }

        HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                SCOPE_HOTEL.equals(scope) ? ACTION_KEY : ACTION_KEY + "." + scope,
                targetType,
                targetId,
                targetLabel,
                "reached=" + reached + " message=" + HousekeepingInputGuard.auditValue(message),
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, reached, ""));
    }

    /**
     * "hotel", "staff", "user:&lt;name&gt;" or "room:&lt;id&gt;" as {scope, target}. One optional
     * trailing string keeps the packet readable by a server that knows only the message.
     */
    static String[] parseRecipient(String value) {
        String recipient = HousekeepingInputGuard.normalize(value);
        int separator = recipient.indexOf(':');

        if (separator < 0) return new String[] {recipient.isEmpty() ? SCOPE_HOTEL : recipient, ""};

        return new String[] {
            recipient.substring(0, separator),
            recipient.substring(separator + 1).trim()
        };
    }

    static int parseRoomId(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
