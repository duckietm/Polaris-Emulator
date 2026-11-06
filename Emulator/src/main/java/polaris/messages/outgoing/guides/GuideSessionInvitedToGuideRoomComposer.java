package polaris.messages.outgoing.guides;

import polaris.hotel.rooms.Room;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuideSessionInvitedToGuideRoomComposer extends MessageComposer {
    private final Room room;

    public GuideSessionInvitedToGuideRoomComposer(Room room) {
        this.room = room;
    }

    // Helper invites noob
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuideSessionInvitedToGuideRoomComposer);
        this.response.appendInt(this.room != null ? this.room.getId() : 0);
        this.response.appendString(this.room != null ? this.room.getName() : "");
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
