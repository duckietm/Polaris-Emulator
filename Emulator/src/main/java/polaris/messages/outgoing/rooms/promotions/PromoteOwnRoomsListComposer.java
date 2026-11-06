package polaris.messages.outgoing.rooms.promotions;

import java.util.ArrayList;
import java.util.List;
import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class PromoteOwnRoomsListComposer extends MessageComposer {
    private final List<Room> rooms = new ArrayList<>();

    public PromoteOwnRoomsListComposer(List<Room> rooms) {
        for (Room room : rooms) {
            if (!room.isPromoted()) this.rooms.add(room);
        }
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.PromoteOwnRoomsListComposer);
        this.response.appendBoolean(true);
        this.response.appendInt(this.rooms.size());
        for (Room room : this.rooms) {
            this.response.appendInt(room.getId());
            this.response.appendString(room.getName());
            this.response.appendBoolean(true); // IDK what the fuck this is.
        }
        return this.response;
    }

    public List<Room> getRooms() {
        return rooms;
    }
}
