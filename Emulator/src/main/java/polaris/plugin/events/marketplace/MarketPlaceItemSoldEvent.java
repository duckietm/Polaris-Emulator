package polaris.plugin.events.marketplace;

import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class MarketPlaceItemSoldEvent extends MarketPlaceEvent {
    public final User seller;
    public final User purchaser;
    public final UserItem item;
    public int price;

    public MarketPlaceItemSoldEvent(User seller, User purchaser, UserItem item, int price) {
        this.seller = seller;
        this.purchaser = purchaser;
        this.item = item;
        this.price = price;
    }
}
