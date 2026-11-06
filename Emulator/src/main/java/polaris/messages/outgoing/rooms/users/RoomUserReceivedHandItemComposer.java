package polaris.messages.outgoing.rooms.users;

import polaris.hotel.rooms.RoomUnit;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserReceivedHandItemComposer extends MessageComposer {
    private RoomUnit from;
    private int handItem;

    public RoomUserReceivedHandItemComposer(RoomUnit from, int handItem) {
        this.from = from;
        this.handItem = handItem;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserReceivedHandItemComposer);
        this.response.appendInt(this.from.getId());
        this.response.appendInt(this.handItem);
        return this.response;
    }

    public RoomUnit getFrom() {
        return from;
    }

    public int getHandItem() {
        return handItem;
    }
}
