package polaris.messages.outgoing.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomFilterWordsComposer extends MessageComposer {
    private final Room room;

    public RoomFilterWordsComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomFilterWordsComposer);

        this.response.appendInt(this.room.getWordFilterWords().size());

        for (String string : this.room.getWordFilterWords()) {
            this.response.appendString(string);
        }

        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
