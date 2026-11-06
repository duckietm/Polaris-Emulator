package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;

public class ModToolChangeRoomSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int roomId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(roomId)) {
                return;
            }

            Room room = ModToolRoomLookup.find(Emulator.getGameEnvironment().getRoomManager(), roomId);

            if (room != null) {
                final boolean lockDoor = this.packet.readInt() == 1;
                final boolean changeTitle = this.packet.readInt() == 1;
                final boolean kickUsers = this.packet.readInt() == 1;

                Emulator.getGameEnvironment()
                        .getModToolManager()
                        .roomAction(room, this.client.getUser(), kickUsers, lockDoor, changeTitle);
            }
        } else {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.modtools.roomsettings")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
        }
    }
}
