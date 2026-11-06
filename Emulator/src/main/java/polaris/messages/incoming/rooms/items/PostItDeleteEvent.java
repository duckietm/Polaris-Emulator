package polaris.messages.incoming.rooms.items;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionExternalImage;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.items.RemoveWallItemComposer;
import polaris.threading.runnables.QueryDeleteUserItem;

public class PostItDeleteEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (item instanceof InteractionPostIt || item instanceof InteractionExternalImage) {
            if (item.getUserId() == this.client.getUser().getUserInfo().getId()
                    || room.getOwnerId() == this.client.getUser().getUserInfo().getId()
                    || this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)) {
                item.setRoomId(0);
                room.removeUserItem(item);
                room.sendComposer(new RemoveWallItemComposer(item).compose());
                Emulator.getThreading().runPersistence(new QueryDeleteUserItem(item.getId()));
            }
        }
    }
}
