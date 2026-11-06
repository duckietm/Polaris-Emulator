package polaris.hotel.items.interactions.wired.triggers;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredTriggerType;
import polaris.hotel.wired.core.WiredEvent;

public class WiredTriggerUserClicksTile extends WiredTriggerUserClicksFurni {
    public static final WiredTriggerType type = WiredTriggerType.CLICKS_TILE;

    private static final String CLICK_TILE_INTERACTION = "room_invisible_click_tile";

    public WiredTriggerUserClicksTile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerUserClicksTile(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(UserItem triggerItem, WiredEvent event) {
        if (!super.matches(triggerItem, event)) {
            return false;
        }

        UserItem sourceItem = event.getSourceItem().orElse(null);
        return isClickTileItem(sourceItem);
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    private boolean isClickTileItem(UserItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interaction = item.getBaseItem().getInteractionType().getName();
        return interaction != null && interaction.equalsIgnoreCase(CLICK_TILE_INTERACTION);
    }

    @Override
    protected boolean isSelectableItem(UserItem item) {
        return this.isClickTileItem(item);
    }

    @Override
    protected String getInvalidSelectionErrorKey() {
        return "wiredfurni.error.require_click_tiles";
    }
}
