package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;

public class ModToolRoomAlertEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            this.packet.readInt();
            String message = ModToolInputGuard.normalize(this.packet.readString());

            if (!ModToolInputGuard.isSafeMessage(message)) {
                return;
            }

            Room room = this.client.getUser().getUserInfo().getCurrentRoom();
            if (room != null) {
                room.alert(message);
            }
        } else {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.roomalert")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
        }
    }
}
