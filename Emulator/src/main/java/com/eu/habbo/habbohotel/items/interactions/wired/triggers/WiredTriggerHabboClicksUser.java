package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredTriggerHabboClicksUser extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.CLICKS_USER;
    private static final String CACHE_BLOCK_MENU_OPEN = "wired.click_user.block_menu_open";
    private static final String CACHE_IGNORE_LOOK_UNTIL = "wired.click_user.ignore_look_until";
    private static final long IGNORE_LOOK_WINDOW_MS = 500L;
    private boolean blockMenuOpen = false;
    private boolean doNotRotate = false;

    public WiredTriggerHabboClicksUser(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerHabboClicksUser(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        return event.getActor().isPresent();
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.blockMenuOpen, this.doNotRotate));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        this.blockMenuOpen = false;
        this.doNotRotate = false;

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = com.eu.habbo.habbohotel.wired.core.WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.blockMenuOpen = data.blockMenuOpen;
                this.doNotRotate = data.doNotRotate;
            }
        }
    }

    @Override
    public void onPickUp() {
        this.blockMenuOpen = false;
        this.doNotRotate = false;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(5);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.blockMenuOpen ? 1 : 0);
        message.appendInt(this.doNotRotate ? 1 : 0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.blockMenuOpen = (params.length > 0) && (params[0] == 1);
        this.doNotRotate = (params.length > 1) && (params[1] == 1);
        return true;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    public boolean isBlockMenuOpen() {
        return this.blockMenuOpen;
    }

    public boolean isDoNotRotate() {
        return this.doNotRotate;
    }

    public static void clearRuntimeFlags(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return;
        }

        roomUnit.getCacheable().remove(CACHE_BLOCK_MENU_OPEN);
        roomUnit.getCacheable().remove(CACHE_IGNORE_LOOK_UNTIL);
    }

    public static void applyRuntimeOptions(RoomUnit roomUnit, boolean blockMenuOpen, boolean doNotRotate) {
        if (roomUnit == null) {
            return;
        }

        if (blockMenuOpen) {
            roomUnit.getCacheable().put(CACHE_BLOCK_MENU_OPEN, Boolean.TRUE);
        }

        if (doNotRotate) {
            roomUnit.getCacheable().put(CACHE_IGNORE_LOOK_UNTIL, System.currentTimeMillis() + IGNORE_LOOK_WINDOW_MS);
        }
    }

    public static boolean consumeBlockMenuOpen(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return false;
        }

        Object value = roomUnit.getCacheable().remove(CACHE_BLOCK_MENU_OPEN);
        return Boolean.TRUE.equals(value);
    }

    public static boolean consumeIgnoreLook(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return false;
        }

        Object value = roomUnit.getCacheable().get(CACHE_IGNORE_LOOK_UNTIL);

        if (!(value instanceof Long)) {
            roomUnit.getCacheable().remove(CACHE_IGNORE_LOOK_UNTIL);
            return false;
        }

        long expiresAt = (Long) value;
        roomUnit.getCacheable().remove(CACHE_IGNORE_LOOK_UNTIL);

        return System.currentTimeMillis() <= expiresAt;
    }

    public static boolean hasPendingIgnoreLook(RoomUnit roomUnit) {
        if (roomUnit == null) {
            return false;
        }

        Object value = roomUnit.getCacheable().get(CACHE_IGNORE_LOOK_UNTIL);

        if (!(value instanceof Long)) {
            return false;
        }

        return System.currentTimeMillis() <= (Long) value;
    }

    static class JsonData {
        boolean blockMenuOpen;
        boolean doNotRotate;

        public JsonData(boolean blockMenuOpen, boolean doNotRotate) {
            this.blockMenuOpen = blockMenuOpen;
            this.doNotRotate = doNotRotate;
        }
    }
}
