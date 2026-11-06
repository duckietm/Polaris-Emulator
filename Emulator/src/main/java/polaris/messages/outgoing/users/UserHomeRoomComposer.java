package polaris.messages.outgoing.users;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class UserHomeRoomComposer extends MessageComposer {
    private final int homeRoom;
    private final int roomToEnter;

    public UserHomeRoomComposer(int homeRoom, int roomToEnter) {
        this.homeRoom = homeRoom;
        this.roomToEnter = roomToEnter;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UserHomeRoomComposer);
        this.response.appendInt(this.homeRoom);
        this.response.appendInt(this.roomToEnter);
        return this.response;
    }

    public int getHomeRoom() {
        return homeRoom;
    }

    public int getRoomToEnter() {
        return roomToEnter;
    }
}
