package polaris.messages.outgoing.rooms;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** FlatAccessible, the doorbell was answered (AIR {@code ?_-bt?/?_-L1W?}): the room id, then the user name. */
public class HideDoorbellComposer extends MessageComposer {
    private final int roomId;
    private final String username;

    /** Kept for plugins; sends room id 0. */
    public HideDoorbellComposer(String username) {
        this(0, username);
    }

    /** The doorbell of {@code user} at room {@code roomId} is gone (they left the queue or were let in). */
    public HideDoorbellComposer(int roomId, User user) {
        this(roomId, user.getUserInfo().getUsername());
    }

    public HideDoorbellComposer(int roomId, String username) {
        this.roomId = roomId;
        this.username = username;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HideDoorbellComposer);
        this.response.appendInt(this.roomId);
        this.response.appendString(this.username);
        return this.response;
    }

    public int getRoomId() {
        return roomId;
    }

    public String getUsername() {
        return username;
    }
}
