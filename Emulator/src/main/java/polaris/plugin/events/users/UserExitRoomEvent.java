package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserExitRoomEvent extends UserEvent {
    public final UserExitRoomReason reason;

    public UserExitRoomEvent(User user, UserExitRoomReason reason) {
        super(user);
        this.reason = reason;
    }

    public enum UserExitRoomReason {
        DOOR(false),
        KICKED_USER(false),
        KICKED_IDLE(true),
        TELEPORT(false);

        public final boolean cancellable;

        UserExitRoomReason(boolean cancellable) {
            this.cancellable = cancellable;
        }
    }
}
