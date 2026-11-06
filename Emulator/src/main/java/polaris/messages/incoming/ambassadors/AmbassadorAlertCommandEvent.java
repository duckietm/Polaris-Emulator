package polaris.messages.incoming.ambassadors;

import polaris.Emulator;
import polaris.hotel.commands.CommandTargetGuard;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.plugin.events.support.SupportUserAlertedEvent;
import polaris.plugin.events.support.SupportUserAlertedReason;

public class AmbassadorAlertCommandEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().hasPermission(Permission.ACC_AMBASSADOR)) {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.modtools.alert")
                            .replace(
                                    "%username%", client.getUser().getUserInfo().getUsername())
                            .replace("%message%", "${notification.ambassador.alert.warning.message}"));
            return;
        }

        int userId = this.packet.readInt();

        // Outside a room, or for a user whose connection is closing, this threw and logged a stack trace each time.
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null) return;

        User user = room.getUser(userId);

        if (user == null || user.getClient() == null) return;

        // An ambassador alerted any user in the room, staff above them included.
        if (!CommandTargetGuard.canTarget(this.client.getUser(), user)) return;

        SupportUserAlertedEvent alertedEvent = new SupportUserAlertedEvent(
                client.getUser(),
                user,
                "${notification.ambassador.alert.warning.message}",
                SupportUserAlertedReason.AMBASSADOR);

        if (Emulator.getPluginManager().fireEvent(alertedEvent).isCancelled()) return;

        user.getClient().sendResponse(new BubbleAlertComposer("ambassador.alert.warning"));
    }
}
