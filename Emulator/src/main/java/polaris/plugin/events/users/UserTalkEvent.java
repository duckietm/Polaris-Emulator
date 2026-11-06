package polaris.plugin.events.users;

import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatType;
import polaris.hotel.users.User;

public class UserTalkEvent extends UserEvent {
    public final RoomChatMessage chatMessage;
    public final RoomChatType chatType;

    public UserTalkEvent(User user, RoomChatMessage chatMessage, RoomChatType chatType) {
        super(user);
        this.chatMessage = chatMessage;
        this.chatType = chatType;
    }
}
