package polaris.messages.outgoing.rooms.items;

import java.util.List;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomConfInvisSupport;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ConfInvisStateComposer extends MessageComposer {
    private final int roomId;
    private final boolean active;
    private final List<Integer> hiddenItemIds;

    public ConfInvisStateComposer(Room room) {
        this.roomId = (room != null) ? room.getId() : 0;
        this.active = RoomConfInvisSupport.hasActiveController(room);
        this.hiddenItemIds = RoomConfInvisSupport.collectHiddenFloorItemIds(room);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ConfInvisStateComposer);
        this.response.appendInt(this.roomId);
        this.response.appendBoolean(this.active);
        this.response.appendInt(this.hiddenItemIds.size());

        for (int itemId : this.hiddenItemIds) {
            this.response.appendInt(BuildersClubItemIds.toWire(itemId));
        }

        return this.response;
    }
}
