package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionLessTimeElapsed extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.TIME_LESS_THAN;

    private int cycles;

    public WiredConditionLessTimeElapsed(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionLessTimeElapsed(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return (Emulator.getIntUnixTimestamp() - ctx.room().getLastTimerReset()) / 0.5 < this.cycles;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.cycles
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        try {
            if (wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                this.cycles = WiredConditionInputGuard.normalizeTimerCycles(data.cycles);
            } else {
                if (!wiredData.equals(""))
                    this.cycles = WiredConditionInputGuard.normalizeTimerCycles(Integer.parseInt(wiredData));
            }
        } catch (Exception e) {
            this.cycles = 0;
        }
    }

    @Override
    public void onPickUp() {
        this.cycles = 0;
    }

    @Override
    public WiredConditionType getType() {
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
        message.appendInt(1);
        message.appendInt(this.cycles);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 1) return false;
        this.cycles = WiredConditionInputGuard.normalizeTimerCycles(settings.getIntParams()[0]);
        return true;
    }

    int normalizeCycles(int value) {
        return Math.max(0, Math.min(WiredConditionMoreTimeElapsed.MAX_CYCLES, value));
    }

    static class JsonData {
        int cycles;

        public JsonData(int cycles) {
            this.cycles = cycles;
        }
    }
}
