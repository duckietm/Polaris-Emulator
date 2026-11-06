package polaris.plugin.events.users.catalog;

import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.users.User;
import polaris.plugin.events.users.UserEvent;

public class UserCatalogEvent extends UserEvent {

    public CatalogItem catalogItem;

    public UserCatalogEvent(User user, CatalogItem catalogItem) {
        super(user);

        this.catalogItem = catalogItem;
    }
}
