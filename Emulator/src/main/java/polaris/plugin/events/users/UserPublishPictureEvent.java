package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserPublishPictureEvent extends UserEvent {

    public String URL;

    public int timestamp;

    public int roomId;

    public UserPublishPictureEvent(User user, String url, int timestamp, int roomId) {
        super(user);
        this.URL = url;
        this.timestamp = timestamp;
        this.roomId = roomId;
    }
}
