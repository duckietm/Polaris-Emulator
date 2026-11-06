package polaris.plugin.events.users.catalog;

import java.util.List;
import java.util.Set;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class UserCatalogItemPurchasedEvent extends UserCatalogEvent {

    public final Set<UserItem> itemsList;

    public int totalCredits;

    public int totalPoints;

    public List<String> badges;

    public UserCatalogItemPurchasedEvent(
            User user,
            CatalogItem catalogItem,
            Set<UserItem> itemsList,
            int totalCredits,
            int totalPoints,
            List<String> badges) {
        super(user, catalogItem);

        this.itemsList = itemsList;
        this.totalCredits = totalCredits;
        this.totalPoints = totalPoints;
        this.badges = badges;
    }
}
