package polaris.plugin.events.users;

import polaris.hotel.users.User;
import polaris.hotel.users.inventory.WardrobeComponent;

public class UserSavedWardrobeEvent extends UserEvent {
    public final WardrobeComponent.WardrobeItem wardrobeItem;

    public UserSavedWardrobeEvent(User user, WardrobeComponent.WardrobeItem wardrobeItem) {
        super(user);
        this.wardrobeItem = wardrobeItem;
    }
}
