package polaris.messages.outgoing.rooms;

import java.util.Map;
import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomRightsListComposer extends MessageComposer {
    private final Room room;

    public RoomRightsListComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomRightsListComposer);
        this.response.appendInt(this.room.getId());

        Map<Integer, String> rightsMap = this.room.getUsersWithRights();

        this.response.appendInt(rightsMap.size());

        for (Map.Entry<Integer, String> set : rightsMap.entrySet()) {
            this.response.appendInt(set.getKey());
            this.response.appendString(set.getValue());
        }

        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
