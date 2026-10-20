package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredRoomTime;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionMoreTimeElapsed extends InteractionWiredCondition {
    private static final WiredConditionType type = WiredConditionType.TIME_MORE_THAN;
    static final int MAX_CYCLES = 1_000_000;

    private int cycles;

    public WiredConditionMoreTimeElapsed(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionMoreTimeElapsed(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        // Half-seconds since the timers were reset, counted in milliseconds rather than whole seconds.
        return WiredRoomTime.millisSinceTimerReset(ctx.room(), System.currentTimeMillis()) / 500.0 > this.cycles;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.cycles));
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
        // AIR stores the slider value plus one ([value + 1], read back as param - 1); ours keeps the
        // half-seconds the slider shows, so saved boxes keep their meaning.
        message.appendInt(1);
        message.appendInt(toOfficial(this.cycles));
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings.getIntParams().length < 1) return false;
        this.cycles = fromOfficial(settings.getIntParams()[0]);
        return true;
    }

    /** The official param of the half-seconds the slider shows: the value plus one. */
    static int toOfficial(int cycles) {
        return cycles + 1;
    }

    /** The half-seconds of an official param (the slider value plus one); never below 0. */
    static int fromOfficial(int param) {
        return WiredConditionInputGuard.normalizeTimerCycles(param - 1);
    }

    int normalizeCycles(int value) {
        return Math.max(0, Math.min(MAX_CYCLES, value));
    }

    static class JsonData {
        int cycles;

        public JsonData(int cycles) {
            this.cycles = cycles;
        }
    }
}
