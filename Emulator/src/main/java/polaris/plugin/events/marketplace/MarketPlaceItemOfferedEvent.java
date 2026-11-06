package polaris.plugin.events.marketplace;

import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class MarketPlaceItemOfferedEvent extends MarketPlaceEvent {
    public final User user;
    public final UserItem item;
    public int price;

    public MarketPlaceItemOfferedEvent(User user, UserItem item, int price) {
        this.user = user;
        this.item = item;
        this.price = price;
    }
}
