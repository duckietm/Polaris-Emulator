package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModToolRoomChatlogComposer;

public class ModToolRequestRoomUserChatlogEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int userId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(userId)) {
                return;
            }

            User user = Emulator.getGameEnvironment().getUserManager().getUser(userId);

            if (user != null) {
                Room room = this.client.getUser().getUserInfo().getCurrentRoom();

                if (room != null) {
                    this.client.sendResponse(new ModToolRoomChatlogComposer(
                            room,
                            Emulator.getGameEnvironment().getModToolManager().getRoomChatlog(room.getId())));
                }
            }
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
