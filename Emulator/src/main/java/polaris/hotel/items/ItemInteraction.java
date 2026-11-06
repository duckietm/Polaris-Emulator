package polaris.hotel.items;

import polaris.hotel.users.UserItem;

public class ItemInteraction {
    private final String name;
    private final Class<? extends UserItem> type;

    public ItemInteraction(String name, Class<? extends UserItem> type) {
        this.name = name;
        this.type = type;
    }

    public Class<? extends UserItem> getType() {
        return this.type;
    }

    public String getName() {
        return this.name;
    }
}
