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

public class WiredConditionDateRangeActive extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.DATE_RANGE;

    private int startDate;
    private int endDate;

    public WiredConditionDateRangeActive(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionDateRangeActive(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
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
        // Official layout: [] with no start, [start] with no end, otherwise [start, end] (Unix seconds).
        int[] params = officialParams(this.startDate, this.endDate);
        message.appendInt(params.length);
        for (int param : params) {
            message.appendInt(param);
        }
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.startDate);
        message.appendInt(this.endDate);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        // AIR sends [] when the start does not parse, [start] when the end does not, else [start, end].
        int[] params = settings.getIntParams();
        this.applyRange(params.length > 0 ? params[0] : 0, params.length > 1 ? params[1] : 0);
        return true;
    }

    /** The official param list of a stored range: empty without a start, the start alone without an end. */
    static int[] officialParams(int startDate, int endDate) {
        if (startDate <= 0 && endDate <= 0) return new int[0];
        if (endDate <= 0) return new int[] {startDate};
        return new int[] {startDate, endDate};
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return isActive(this.startDate, this.endDate, Emulator.getIntUnixTimestamp());
    }

    /**
     * Official rule: a range without an end date (0) never passes. A start of 0 means "since always". Behaviour
     * observed in plus-emulator/PlusEMU 06b4ae9a, reimplemented.
     */
    static boolean isActive(int startDate, int endDate, int now) {
        return endDate > 0 && startDate <= now && endDate >= now;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.startDate, this.endDate));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            this.applyRange(0, 0);
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            if (data == null) {
                this.applyRange(0, 0);
                return;
            }
            this.applyRange(data.startDate, data.endDate);
        } else {
            String[] data = wiredData.split("\t");

            if (data.length == 2) {
                try {
                    this.applyRange(Integer.parseInt(data[0]), Integer.parseInt(data[1]));
                } catch (Exception e) {
                    this.applyRange(0, 0);
                }
            }
        }
    }

    @Override
    public void onPickUp() {
        this.startDate = 0;
        this.endDate = 0;
    }

    private void applyRange(int startDate, int endDate) {
        int[] range = WiredDateRangeInputGuard.normalizeRange(startDate, endDate);
        this.startDate = range[0];
        this.endDate = range[1];
    }

    static class JsonData {
        int startDate;
        int endDate;

        public JsonData(int startDate, int endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
}
