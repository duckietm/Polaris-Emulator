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

/**
 * Fires when a room unit receives a hand item. The matching {@code USER_GETS_HANDITEM} event is raised
 * by {@code RoomUnitManager#giveHandItem(RoomUnit, int)} — the single funnel every hand-item grant
 * (the {@code WiredEffectGiveHandItem} effect, the {@code :handitem} command, butler bots, etc.) flows
 * through — and routed by the engine to this trigger via {@link WiredTriggerType#USER_GETS_HANDITEM}.
 *
 * <p>The dialog carries one optional int param {@code [handItemId]}: when {@code 0} the trigger fires
 * for <em>any</em> hand item; otherwise it only fires when the freshly-granted item id matches. The
 * actor's {@link RoomUnit#getHandItem()} already reflects the just-granted id at raise time (the event
 * is built after {@code setHandItem}), so {@link #matches} reads it directly and no extra event field is
 * needed.</p>
 *
 * <p>Serialization mirrors the single-int trigger shape: it appends {@code intParamCount=1} and the
 * stored {@code handItemId}, so the client reads/saves exactly one int and
 * {@link #saveData(WiredSettings)} reads it back in the same slot.</p>
 */
public class WiredTriggerUserGetsHandItem extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.USER_GETS_HANDITEM;

    private static final int ANY_HAND_ITEM = 0;

    private int handItemId = ANY_HAND_ITEM;

    public WiredTriggerUserGetsHandItem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerUserGetsHandItem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        RoomUnit actor = event.getActor().orElse(null);
        if (actor == null) {
            return false;
        }

        return this.handItemId == ANY_HAND_ITEM || actor.getHandItem() == this.handItemId;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredTriggerType getType() {
        return type;
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(WiredManager.MAXIMUM_FURNI_SELECTION);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(1);
        message.appendInt(this.handItemId);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.handItemId = (params.length > 0) ? Math.max(ANY_HAND_ITEM, params[0]) : ANY_HAND_ITEM;
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.handItemId));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.handItemId = ANY_HAND_ITEM;
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.handItemId = Math.max(ANY_HAND_ITEM, data.handItemId);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.handItemId = ANY_HAND_ITEM;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return true;
    }

    static class JsonData {
        int handItemId;

        public JsonData(int handItemId) {
            this.handItemId = handItemId;
        }
    }
}
