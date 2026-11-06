package polaris.plugin.events.users;

import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatType;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;

public class UsernameTalkEvent extends UserEvent {
    public final RoomChatMessage chatMessage;
    public final RoomChatType chatType;

    private ServerMessage customComposer = null;

    public UsernameTalkEvent(User user, RoomChatMessage chatMessage, RoomChatType chatType) {
        super(user);
        this.chatMessage = chatMessage;
        this.chatType = chatType;
    }

    public void setCustomComposer(ServerMessage customComposer) {
        this.customComposer = customComposer;
    }

    public boolean hasCustomComposer() {
        return this.customComposer != null;
    }

    public ServerMessage getCustomComposer() {
        return this.customComposer;
    }
}
