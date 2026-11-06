package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserIdleEvent extends UserEvent {
    public final IdleReason reason;
    public boolean idle;

    public UserIdleEvent(User user, IdleReason reason, boolean idle) {
        super(user);

        this.reason = reason;
        this.idle = idle;
    }

    public enum IdleReason {
        ACTION,
        DANCE,
        TIMEOUT,
        WALKED,
        TALKED
    }
}
