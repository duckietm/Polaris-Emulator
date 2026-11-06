package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.core.WiredRoomTime;
import polaris.messages.ServerMessage;

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
        // AIR counts whole pulses since the reset and compares them with the value the editor sent
        // (More Than: >=); the box stores that value minus one.
        return WiredConditionLessTimeElapsed.pulsesSinceReset(
                        WiredRoomTime.millisSinceTimerReset(ctx.room(), System.currentTimeMillis()))
                >= this.cycles + 1;
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
