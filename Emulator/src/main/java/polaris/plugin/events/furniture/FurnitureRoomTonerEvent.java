package polaris.plugin.events.furniture;

import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class FurnitureRoomTonerEvent extends FurnitureUserEvent {
    public int hue;
    public int saturation;
    public int brightness;

    public FurnitureRoomTonerEvent(UserItem furniture, User user, int hue, int saturation, int brightness) {
        super(furniture, user);

        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
    }
}
