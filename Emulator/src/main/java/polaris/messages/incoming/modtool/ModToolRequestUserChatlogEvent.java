package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModToolUserChatlogComposer;

public class ModToolRequestUserChatlogEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int userId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(userId)) {
                return;
            }

            UserInfo userInfo = UserManager.getOfflineUserInfo(userId);
            if (userInfo == null) {
                return;
            }
            String username = userInfo.getUsername();

            this.client.sendResponse(new ModToolUserChatlogComposer(
                    Emulator.getGameEnvironment().getModToolManager().getUserRoomVisitsAndChatlogs(userId),
                    userId,
                    username));
        } else {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.chatlog")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
        }
    }
}
