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
 * Fires when a dice furni is rolled (its face value finalized). The DICE_ROLLED event is raised by
 * {@code RandomDiceNumber#run()} (guarded to {@code InteractionDice}) and routed by the engine via
 * {@link WiredTriggerType#DICE_ROLLED}. The event's source item is the dice, whose extradata holds the
 * rolled value. One optional int param {@code [requiredValue]}: {@code 0} fires for any roll; otherwise
 * it fires only when the rolled value equals it.
 */
public class WiredTriggerDiceRolled extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.DICE_ROLLED;
    private static final int ANY_VALUE = 0;

    private int requiredValue = ANY_VALUE;

    public WiredTriggerDiceRolled(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerDiceRolled(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        HabboItem dice = event.getSourceItem().orElse(null);
        if (dice == null) {
            return false;
        }

        if (this.requiredValue == ANY_VALUE) {
            return true;
        }

        try {
            return Integer.parseInt(dice.getExtradata()) == this.requiredValue;
        } catch (NumberFormatException exception) {
            return false;
        }
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
        message.appendInt(this.requiredValue);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.requiredValue = (params.length > 0) ? Math.max(ANY_VALUE, params[0]) : ANY_VALUE;
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.requiredValue));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.requiredValue = ANY_VALUE;
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.requiredValue = Math.max(ANY_VALUE, data.requiredValue);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.requiredValue = ANY_VALUE;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return false;
    }

    static class JsonData {
        int requiredValue;

        public JsonData(int requiredValue) {
            this.requiredValue = requiredValue;
        }
    }
}
