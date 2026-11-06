package polaris.plugin.events.users.catalog;

import java.util.Set;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

public class UserCatalogFurnitureBoughtEvent extends UserCatalogEvent {

    public final Set<UserItem> furniture;

    public UserCatalogFurnitureBoughtEvent(User user, CatalogItem catalogItem, Set<UserItem> furniture) {
        super(user, catalogItem);

        this.furniture = furniture;
    }
}
