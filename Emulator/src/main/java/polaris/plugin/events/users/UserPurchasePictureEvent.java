package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserPurchasePictureEvent extends UserEvent {

    public String url;

    public int roomId;

    public int timestamp;

    public UserPurchasePictureEvent(User user, String url, int roomId, int timestamp) {
        super(user);

        this.url = url;
        this.roomId = roomId;
        this.timestamp = timestamp;
    }
}
