package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.util.HotelDateTimeUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;

public class WiredConditionMatchTime extends InteractionWiredCondition {
    private static final int MODE_SKIP = 0;
    private static final int MODE_EXACT = 1;
    private static final int MODE_RANGE = 2;

    public static final WiredConditionType type = WiredConditionType.MATCH_TIME;

    private int hourMode = MODE_SKIP;
    private int hourFrom = 0;
    private int hourTo = 0;
    private int minuteMode = MODE_SKIP;
    private int minuteFrom = 0;
    private int minuteTo = 0;
    private int secondMode = MODE_SKIP;
    private int secondFrom = 0;
    private int secondTo = 0;

    public WiredConditionMatchTime(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionMatchTime(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        message.appendInt(9);
        message.appendInt(this.hourMode);
        message.appendInt(this.hourFrom);
        message.appendInt(this.hourTo);
        message.appendInt(this.minuteMode);
        message.appendInt(this.minuteFrom);
        message.appendInt(this.minuteTo);
        message.appendInt(this.secondMode);
        message.appendInt(this.secondFrom);
        message.appendInt(this.secondTo);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();

        this.hourMode = (params.length > 0) ? this.normalizeMode(params[0]) : MODE_SKIP;
        this.hourFrom = (params.length > 1) ? this.normalizeHour(params[1]) : 0;
        this.hourTo = (params.length > 2) ? this.normalizeHour(params[2]) : this.hourFrom;
        this.minuteMode = (params.length > 3) ? this.normalizeMode(params[3]) : MODE_SKIP;
        this.minuteFrom = (params.length > 4) ? this.normalizeMinuteOrSecond(params[4]) : 0;
        this.minuteTo = (params.length > 5) ? this.normalizeMinuteOrSecond(params[5]) : this.minuteFrom;
        this.secondMode = (params.length > 6) ? this.normalizeMode(params[6]) : MODE_SKIP;
        this.secondFrom = (params.length > 7) ? this.normalizeMinuteOrSecond(params[7]) : 0;
        this.secondTo = (params.length > 8) ? this.normalizeMinuteOrSecond(params[8]) : this.secondFrom;

        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        LocalTime now = HotelDateTimeUtil.localTimeNow();

        return this.matchesTimePart(now.getHour(), this.hourMode, this.hourFrom, this.hourTo)
                && this.matchesTimePart(now.getMinute(), this.minuteMode, this.minuteFrom, this.minuteTo)
                && this.matchesTimePart(now.getSecond(), this.secondMode, this.secondFrom, this.secondTo);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.hourMode,
                this.hourFrom,
                this.hourTo,
                this.minuteMode,
                this.minuteFrom,
                this.minuteTo,
                this.secondMode,
                this.secondFrom,
                this.secondTo
        ));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.reset();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data;
            try {
                data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            } catch (RuntimeException exception) {
                this.reset();
                return;
            }

            if (data == null) {
                return;
            }

            this.hourMode = this.normalizeMode(data.hourMode);
            this.hourFrom = this.normalizeHour(data.hourFrom);
            this.hourTo = this.normalizeHour(data.hourTo);
            this.minuteMode = this.normalizeMode(data.minuteMode);
            this.minuteFrom = this.normalizeMinuteOrSecond(data.minuteFrom);
            this.minuteTo = this.normalizeMinuteOrSecond(data.minuteTo);
            this.secondMode = this.normalizeMode(data.secondMode);
            this.secondFrom = this.normalizeMinuteOrSecond(data.secondFrom);
            this.secondTo = this.normalizeMinuteOrSecond(data.secondTo);
            return;
        }

        String[] data = wiredData.split("\t");
        if (data.length != 9) {
            return;
        }

        try {
            this.hourMode = this.normalizeMode(Integer.parseInt(data[0]));
            this.hourFrom = this.normalizeHour(Integer.parseInt(data[1]));
            this.hourTo = this.normalizeHour(Integer.parseInt(data[2]));
            this.minuteMode = this.normalizeMode(Integer.parseInt(data[3]));
            this.minuteFrom = this.normalizeMinuteOrSecond(Integer.parseInt(data[4]));
            this.minuteTo = this.normalizeMinuteOrSecond(Integer.parseInt(data[5]));
            this.secondMode = this.normalizeMode(Integer.parseInt(data[6]));
            this.secondFrom = this.normalizeMinuteOrSecond(Integer.parseInt(data[7]));
            this.secondTo = this.normalizeMinuteOrSecond(Integer.parseInt(data[8]));
        } catch (NumberFormatException ignored) {
            this.reset();
        }
    }

    @Override
    public void onPickUp() {
        this.reset();
    }

    private void reset() {
        this.hourMode = MODE_SKIP;
        this.hourFrom = 0;
        this.hourTo = 0;
        this.minuteMode = MODE_SKIP;
        this.minuteFrom = 0;
        this.minuteTo = 0;
        this.secondMode = MODE_SKIP;
        this.secondFrom = 0;
        this.secondTo = 0;
    }

    private boolean matchesTimePart(int currentValue, int mode, int fromValue, int toValue) {
        switch (mode) {
            case MODE_EXACT:
                return currentValue == fromValue;
            case MODE_RANGE:
                if (fromValue <= toValue) {
                    return currentValue >= fromValue && currentValue <= toValue;
                }

                return currentValue >= fromValue || currentValue <= toValue;
            default:
                return true;
        }
    }

    int normalizeMode(int value) {
        if (value < MODE_SKIP || value > MODE_RANGE) {
            return MODE_SKIP;
        }

        return value;
    }

    int normalizeHour(int value) {
        return Math.max(0, Math.min(23, value));
    }

    int normalizeMinuteOrSecond(int value) {
        return Math.max(0, Math.min(59, value));
    }

    static class JsonData {
        int hourMode;
        int hourFrom;
        int hourTo;
        int minuteMode;
        int minuteFrom;
        int minuteTo;
        int secondMode;
        int secondFrom;
        int secondTo;

        public JsonData(int hourMode, int hourFrom, int hourTo, int minuteMode, int minuteFrom, int minuteTo, int secondMode, int secondFrom, int secondTo) {
            this.hourMode = hourMode;
            this.hourFrom = hourFrom;
            this.hourTo = hourTo;
            this.minuteMode = minuteMode;
            this.minuteFrom = minuteFrom;
            this.minuteTo = minuteTo;
            this.secondMode = secondMode;
            this.secondFrom = secondFrom;
            this.secondTo = secondTo;
        }
    }
}
