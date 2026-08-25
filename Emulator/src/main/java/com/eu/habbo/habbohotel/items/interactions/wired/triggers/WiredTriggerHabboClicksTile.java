package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredTriggerHabboClicksTile extends WiredTriggerHabboClicksFurni {
    public static final WiredTriggerType type = WiredTriggerType.CLICKS_TILE;

    private static final String CLICK_TILE_INTERACTION = "room_invisible_click_tile";

    public WiredTriggerHabboClicksTile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerHabboClicksTile(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (!super.matches(triggerItem, event)) {
            return false;
        }

        HabboItem sourceItem = event.getSourceItem().orElse(null);
        return isClickTileItem(sourceItem);
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    private boolean isClickTileItem(HabboItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interaction = item.getBaseItem().getInteractionType().getName();
        return interaction != null && interaction.equalsIgnoreCase(CLICK_TILE_INTERACTION);
    }

    @Override
    protected boolean isSelectableItem(HabboItem item) {
        return this.isClickTileItem(item);
    }

    @Override
    protected String getInvalidSelectionErrorKey() {
        return "wiredfurni.error.require_click_tiles";
    }
}
