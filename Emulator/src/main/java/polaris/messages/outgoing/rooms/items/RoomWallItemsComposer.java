package polaris.messages.outgoing.rooms.items;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Set;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomWallItemsComposer extends MessageComposer {
    private final Room room;

    public RoomWallItemsComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomWallItemsComposer);
        Int2ObjectMap<String> userNames = this.room.getFurniOwnerNames();

        this.response.appendInt(userNames.size());
        for (Int2ObjectMap.Entry<String> set : userNames.int2ObjectEntrySet()) {
            this.response.appendInt(set.getIntKey());
            this.response.appendString(set.getValue());
        }

        Set<UserItem> items = this.room.getWallItems();

        this.response.appendInt(items.size());
        for (UserItem item : items) {
            item.serializeWallData(this.response);
        }
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
