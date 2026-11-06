package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModeratorActionResultComposer;

public class ModToolKickEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.modtools.kick")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
            return;
        }

        int userId = this.packet.readInt();
        String message = ModToolInputGuard.normalize(this.packet.readString());
        this.packet.readInt(); // Moderator category; kick auditing currently stores the normalized message.

        if (!ModToolTicketGuard.isPositiveId(userId) || !ModToolInputGuard.isSafeMessage(message)) {
            return;
        }

        User target = Emulator.getGameEnvironment().getUserManager().getUser(userId);

        Emulator.getGameEnvironment().getModToolManager().kick(this.client.getUser(), target, message);
        this.client.sendResponse(new ModeratorActionResultComposer(userId, target != null));
    }
}
