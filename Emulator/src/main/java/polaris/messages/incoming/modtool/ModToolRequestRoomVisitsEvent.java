package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.UserInfo;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModToolUserRoomVisitsComposer;

public class ModToolRequestRoomVisitsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int userId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(userId)) {
                return;
            }

            UserInfo userInfo = Emulator.getGameEnvironment().getUserManager().getUserInfo(userId);

            if (userInfo != null) {
                this.client.sendResponse(new ModToolUserRoomVisitsComposer(
                        userInfo,
                        Emulator.getGameEnvironment().getModToolManager().getUserRoomVisits(userId)));
            }
        }
    }
}
