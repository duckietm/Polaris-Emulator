package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomSettingsComposer;

public class RequestRoomSettingsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        final int roomId = this.packet.readInt();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
        if (room == null) return;
        if (!room.hasRights(this.client.getUser()) && !this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER))
            return;

        this.client.sendResponse(new RoomSettingsComposer(room));
    }
}
