package polaris.messages.incoming.housekeeping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.generic.alerts.StaffAlertWithLinkComposer;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Mirrors :ha - staff alert with sender attribution. By default it goes to
 * every online user whose `blockStaffAlerts` flag isn't set; an optional
 * recipient narrows it to one online user (by name), the users in one active
 * room (by id), or the staff (holders of the staff alert permission). A
 * client that sends only the message keeps the hotel-wide broadcast. The
 * alert is composed once and forwarded by reference, so a broadcast is
 * O(N users) wire writes, not O(N) compose calls.
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
        List<User> recipients = new ArrayList<>();
        String targetType = HousekeepingAuditLog.TARGET_HOTEL;
        int targetId = 0;
        String targetLabel = "";

        switch (scope) {
            case SCOPE_HOTEL -> {
                for (User user : environment.getUserManager().getOnlineUsers().values()) {
                    if (user != null && (user.getUserStats() == null || !user.getUserStats().blockStaffAlerts)) {
                        recipients.add(user);
                    }
                }
            }
            case SCOPE_USER -> {
                User user =
                        target.isEmpty() ? null : environment.getUserManager().getUser(target);

                if (user == null) {
                    this.client.sendResponse(new HousekeepingActionResultComposer(
                            ACTION_KEY, false, 0, "housekeeping.error.user_offline"));
                    return;
                }

                recipients.add(user);
                targetType = HousekeepingAuditLog.TARGET_USER;
                targetId = user.getUserInfo().getId();
                targetLabel = user.getUserInfo().getUsername();
            }
            case SCOPE_ROOM -> {
                Room room = environment.getRoomManager().getRoom(parseRoomId(target));

                if (room == null) {
                    this.client.sendResponse(new HousekeepingActionResultComposer(
                            ACTION_KEY, false, 0, "housekeeping.error.room_not_active"));
                    return;
                }

                recipients.addAll(room.getUsers());
                targetType = HousekeepingAuditLog.TARGET_ROOM;
                targetId = room.getId();
                targetLabel = room.getName();
            }
            case SCOPE_STAFF -> {
                Collection<User> online =
                        environment.getUserManager().getOnlineUsers().values();

                for (User user : online) {
                    if (user != null && user.hasPermission(STAFF_PERMISSION)) recipients.add(user);
                }
            }
            default -> {
                this.client.sendResponse(
                        new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
                return;
            }
        }

        String body = message + "\r\n-" + this.client.getUser().getUserInfo().getUsername();
        ServerMessage broadcast = new StaffAlertWithLinkComposer(body, "").compose();

        int reached = 0;

        for (User user : recipients) {
            if (user == null || user.getClient() == null) continue;

            user.getClient().sendResponse(broadcast);
            reached++;
        }

        HousekeepingAuditLog.log(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                SCOPE_HOTEL.equals(scope) ? ACTION_KEY : ACTION_KEY + "." + scope,
                targetType,
                targetId,
                targetLabel,
                "reached=" + reached + " message=" + HousekeepingInputGuard.auditValue(message),
                this.client.getUser().getUserInfo().getIpLogin());
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
