package polaris.messages.outgoing.rooms.users;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class RoomUserNameChangedComposer extends MessageComposer {

    private final int userId;
    private final int roomId;
    private final String name;

    public RoomUserNameChangedComposer(User user) {
        this(user, false);
    }

    public RoomUserNameChangedComposer(User user, boolean includePrefix) {
        this.userId = user.getUserInfo().getId();
        this.roomId = user.getRoomUnit().getId();
        this.name = (includePrefix
                        ? Room.PREFIX_FORMAT
                                .replace("%color%", user.getUserInfo().getRank().getPrefixColor())
                                .replace(
                                        "%prefix%", user.getUserInfo().getRank().getPrefix())
                        : "")
                + user.getUserInfo().getUsername();
    }

    public RoomUserNameChangedComposer(int userId, int roomId, String name) {
        this.userId = userId;
        this.roomId = roomId;
        this.name = name;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserNameChangedComposer);
        this.response.appendInt(this.userId);
        this.response.appendInt(this.roomId);
        this.response.appendString(this.name);
        return this.response;
    }

    public int getUserId() {
        return userId;
    }

    public int getRoomId() {
        return roomId;
    }

    public String getName() {
        return name;
    }
}
