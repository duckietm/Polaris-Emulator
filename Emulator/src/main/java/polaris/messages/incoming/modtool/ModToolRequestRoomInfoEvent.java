package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModToolRoomInfoComposer;

public class ModToolRequestRoomInfoEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int roomId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(roomId)) {
                return;
            }

            Room room = ModToolRoomLookup.find(Emulator.getGameEnvironment().getRoomManager(), roomId);

            if (room != null) {
                this.client.sendResponse(new ModToolRoomInfoComposer(room));
            }
        } else {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.modtools.roominfo")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
        }
    }
}
