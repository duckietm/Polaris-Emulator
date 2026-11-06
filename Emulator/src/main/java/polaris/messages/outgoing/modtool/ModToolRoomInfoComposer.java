package polaris.messages.outgoing.modtool;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class ModToolRoomInfoComposer extends MessageComposer {
    private final Room room;

    public ModToolRoomInfoComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolRoomInfoComposer);
        this.response.appendInt(this.room.getId());
        this.response.appendInt(this.room.getCurrentUsers().size());
        this.response.appendBoolean(this.room.getUser(this.room.getOwnerId()) != null);
        this.response.appendInt(this.room.getOwnerId());
        this.response.appendString(this.room.getOwnerName());
        this.response.appendBoolean(true);
        this.response.appendString(this.room.getName());
        this.response.appendString(this.room.getDescription());
        this.response.appendInt(this.room.getTags().split(";").length);
        for (int i = 0; i < this.room.getTags().split(";").length; i++) {
            this.response.appendString(this.room.getTags().split(";")[i]);
        }
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
