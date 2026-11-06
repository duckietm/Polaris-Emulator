package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserPickGiftEvent extends UserEvent {
    public final int keyA;
    public final int keyB;
    public final int index;

    public UserPickGiftEvent(User user, int keyA, int keyB, int index) {
        super(user);
        this.keyA = keyA;
        this.keyB = keyB;
        this.index = index;
    }
}
