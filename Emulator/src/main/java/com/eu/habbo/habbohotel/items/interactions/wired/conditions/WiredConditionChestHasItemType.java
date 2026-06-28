package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestStorage;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChest;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Chest Contains Furni Type (furni classname {@code wf_cnd_chest_has_item_type}). Passes when the
 * selected {@link InteractionWiredChest}(s) hold at least {@code amount} furni of base type
 * {@code baseItemId}.
 */
public class WiredConditionChestHasItemType extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.CHEST_HAS_ITEM_TYPE;

    private final List<Integer> chestIds = new ArrayList<>();
    private int baseItemId = 0;
    private int amount = 1;

    public WiredConditionChestHasItemType(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionChestHasItemType(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null || this.baseItemId <= 0) return false;

        int total = 0;
        for (Integer id : this.chestIds) {
            HabboItem item = room.getHabboItem(id);
            if (item instanceof InteractionWiredChest chest) {
                total += chest.getContents().count(ChestStorage.KIND_FURNI, this.baseItemId);
            }
        }
        return total >= this.amount;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.baseItemId = (params.length > 0) ? params[0] : 0;
        this.amount = (params.length > 1) ? Math.max(1, params[1]) : 1;

        this.chestIds.clear();
        if (settings.getFurniIds() != null) {
            for (int id : settings.getFurniIds()) {
                this.chestIds.add(id);
            }
        }
        return true;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.baseItemId, this.amount, this.chestIds));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null) return;

        this.baseItemId = data.baseItemId;
        this.amount = Math.max(1, data.amount);
        if (data.chestIds != null) {
            this.chestIds.addAll(data.chestIds);
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(this.chestIds.size());
        for (Integer id : this.chestIds) {
            message.appendInt(id);
        }
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.baseItemId);
        message.appendInt(this.amount);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void onPickUp() {
        this.chestIds.clear();
        this.baseItemId = 0;
        this.amount = 1;
    }

    static class JsonData {
        int baseItemId;
        int amount;
        List<Integer> chestIds;

        public JsonData(int baseItemId, int amount, List<Integer> chestIds) {
            this.baseItemId = baseItemId;
            this.amount = amount;
            this.chestIds = chestIds;
        }
    }
}
