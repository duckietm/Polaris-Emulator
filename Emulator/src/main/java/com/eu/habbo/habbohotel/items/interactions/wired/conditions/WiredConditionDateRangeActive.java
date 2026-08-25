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

    public WiredConditionDateRangeActive(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        message.appendInt(2);
        message.appendInt(this.startDate);
        message.appendInt(this.endDate);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(this.startDate);
        message.appendInt(this.endDate);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if(settings.getIntParams().length < 2) return false;
        this.applyRange(settings.getIntParams()[0], settings.getIntParams()[1]);
        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        int time = Emulator.getIntUnixTimestamp();
        return this.startDate < time && this.endDate >= time;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.startDate,
                this.endDate
        ));
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
