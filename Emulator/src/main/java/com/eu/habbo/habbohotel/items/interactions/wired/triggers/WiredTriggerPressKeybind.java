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
 * Fires when a user presses a configured keybind key in the room. The PRESS_KEYBIND event is raised by
 * the client-&gt;server packet handler {@code PressKeybindEvent} (header 9311) via
 * {@link WiredManager#triggerKeybind(Room, RoomUnit, int)}; the pressed key code travels on the event's
 * {@code actionParameter}. One optional int param {@code [keyCode]}: {@code 0} fires for ANY key,
 * otherwise it fires only when the pressed key code equals it.
 *
 * <p>Client side (Nitro): the trigger dialog must map to {@code WiredTriggerLayout} code
 * {@code PRESS_KEYBIND = 26} (must equal {@link #type}'s {@code code}) and a new outgoing packet must
 * send the pressed key code to header 9311. See {@code docs/plans/press-keybind-implementation-plan.md}.
 */
public class WiredTriggerPressKeybind extends InteractionWiredTrigger {
    public static final WiredTriggerType type = WiredTriggerType.PRESS_KEYBIND;
    private static final int ANY_KEY = 0;

    private int keyCode = ANY_KEY;

    public WiredTriggerPressKeybind(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerPressKeybind(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        if (this.keyCode == ANY_KEY) {
            return true;
        }

        return event.getActionParameter() == this.keyCode;
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
        message.appendInt(this.keyCode);
        message.appendInt(0);
        message.appendInt(type.code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();
        this.keyCode = (params.length > 0) ? Math.max(ANY_KEY, params[0]) : ANY_KEY;
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.keyCode));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.keyCode = ANY_KEY;
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.keyCode = Math.max(ANY_KEY, data.keyCode);
            }
        }
    }

    @Override
    public void onPickUp() {
        this.keyCode = ANY_KEY;
    }

    @Override
    public boolean isTriggeredByRoomUnit() {
        return false;
    }

    static class JsonData {
        int keyCode;

        public JsonData(int keyCode) {
            this.keyCode = keyCode;
        }
    }
}
