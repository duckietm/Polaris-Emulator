package polaris.messages.outgoing.rooms;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** FlatAccessDenied (AIR {@code ?_-H1Z?/?_-T1k?}): the room id, then the user name. */
public class RoomAccessDeniedComposer extends MessageComposer {
    private final int roomId;
    private final String user;

    /** Kept for plugins; sends room id 0. */
    public RoomAccessDeniedComposer(String user) {
        this(0, user);
    }

    public RoomAccessDeniedComposer(int roomId, String user) {
        this.roomId = roomId;
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomAccessDeniedComposer);
        this.response.appendInt(this.roomId);
        this.response.appendString(this.user);
        return this.response;
    }

    public int getRoomId() {
        return roomId;
    }

    public String getUser() {
        return user;
    }
}
