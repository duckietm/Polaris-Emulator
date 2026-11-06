package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModToolIssueResponseAlertComposer;
import polaris.plugin.events.support.SupportUserAlertedReason;

public class ModToolAlertEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int userId = this.packet.readInt();
            String message = ModToolInputGuard.normalize(this.packet.readString());

            if (!ModToolTicketGuard.isPositiveId(userId) || !ModToolInputGuard.isSafeMessage(message)) {
                return;
            }

            User alertedUser = Emulator.getGameEnvironment().getUserManager().getUser(userId);

            if (alertedUser == null) return;

            // Writing to somebody whose report you are holding is answering that report: the client
            // shows it under the call for help, not as a warning out of nowhere.
            if (Emulator.getGameEnvironment()
                            .getModToolManager()
                            .pickedTicketOf(
                                    this.client.getUser().getUserInfo().getId(),
                                    alertedUser.getUserInfo().getId())
                    != null) {
                alertedUser.getClient().sendResponse(new ModToolIssueResponseAlertComposer(message));
                return;
            }

            Emulator.getGameEnvironment()
                    .getModToolManager()
                    .alert(this.client.getUser(), alertedUser, message, SupportUserAlertedReason.ALERT);
        } else {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.modtools.kick")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
        }
    }
}
