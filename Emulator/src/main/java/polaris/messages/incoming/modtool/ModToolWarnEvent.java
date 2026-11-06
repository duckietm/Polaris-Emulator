package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.plugin.events.support.SupportUserAlertedReason;

public class ModToolWarnEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int userId = this.packet.readInt();
            String message = ModToolInputGuard.normalize(this.packet.readString());

            if (!ModToolTicketGuard.isPositiveId(userId) || !ModToolInputGuard.isSafeMessage(message)) {
                return;
            }

            User alertedUser = Emulator.getGameEnvironment().getUserManager().getUser(userId);

            if (alertedUser != null)
                Emulator.getGameEnvironment()
                        .getModToolManager()
                        .alert(this.client.getUser(), alertedUser, message, SupportUserAlertedReason.CAUTION);
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
