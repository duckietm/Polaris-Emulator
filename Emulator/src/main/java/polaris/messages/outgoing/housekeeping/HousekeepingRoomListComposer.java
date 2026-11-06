package polaris.messages.outgoing.housekeeping;

import java.util.List;
import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HousekeepingRoomListComposer extends MessageComposer {
    private final List<Room> rooms;

    public HousekeepingRoomListComposer(List<Room> rooms) {
        this.rooms = rooms;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HousekeepingRoomListComposer);
        this.response.appendInt(this.rooms != null ? this.rooms.size() : 0);

        if (this.rooms != null) {
            for (Room room : this.rooms) {
                HousekeepingRoomDetailComposer.appendRoomFields(this.response, room);
            }
        }

        return this.response;
    }
}
