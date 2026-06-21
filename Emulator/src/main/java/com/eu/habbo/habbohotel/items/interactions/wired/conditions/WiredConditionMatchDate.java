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
import java.time.LocalDate;

public class WiredConditionMatchDate extends InteractionWiredCondition {
    private static final int MODE_SKIP = 0;
    private static final int MODE_EXACT = 1;
    private static final int MODE_RANGE = 2;
    private static final int ALL_WEEKDAYS_MASK = createMask(1, 7);
    private static final int ALL_MONTHS_MASK = createMask(1, 12);

    public static final WiredConditionType type = WiredConditionType.MATCH_DATE;

    private int weekdayMask = ALL_WEEKDAYS_MASK;
    private int dayMode = MODE_SKIP;
    private int dayFrom = 1;
    private int dayTo = 31;
    private int monthMask = ALL_MONTHS_MASK;
    private int yearMode = MODE_SKIP;
    private int yearFrom = HotelDateTimeUtil.localDateNow().getYear();
    private int yearTo = HotelDateTimeUtil.localDateNow().getYear();

    public WiredConditionMatchDate(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionMatchDate(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
        message.appendInt(8);
        message.appendInt(this.weekdayMask);
        message.appendInt(this.dayMode);
        message.appendInt(this.dayFrom);
        message.appendInt(this.dayTo);
        message.appendInt(this.monthMask);
        message.appendInt(this.yearMode);
        message.appendInt(this.yearFrom);
        message.appendInt(this.yearTo);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        int[] params = settings.getIntParams();

        this.weekdayMask = (params.length > 0) ? this.normalizeWeekdayMask(params[0]) : ALL_WEEKDAYS_MASK;
        this.dayMode = (params.length > 1) ? this.normalizeMode(params[1]) : MODE_SKIP;
        this.dayFrom = (params.length > 2) ? this.normalizeDay(params[2]) : 1;
        this.dayTo = (params.length > 3) ? this.normalizeDay(params[3]) : this.dayFrom;
        this.monthMask = (params.length > 4) ? this.normalizeMonthMask(params[4]) : ALL_MONTHS_MASK;
        this.yearMode = (params.length > 5) ? this.normalizeMode(params[5]) : MODE_SKIP;
        this.yearFrom = (params.length > 6) ? this.normalizeYear(params[6]) : HotelDateTimeUtil.localDateNow().getYear();
        this.yearTo = (params.length > 7) ? this.normalizeYear(params[7]) : this.yearFrom;

        return true;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        LocalDate now = HotelDateTimeUtil.localDateNow();

        return this.matchesMask(now.getDayOfWeek().getValue(), this.weekdayMask)
                && this.matchesMask(now.getMonthValue(), this.monthMask)
                && this.matchesDatePart(now.getDayOfMonth(), this.dayMode, this.dayFrom, this.dayTo)
                && this.matchesDatePart(now.getYear(), this.yearMode, this.yearFrom, this.yearTo);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(
                this.weekdayMask,
                this.dayMode,
                this.dayFrom,
                this.dayTo,
                this.monthMask,
                this.yearMode,
                this.yearFrom,
                this.yearTo
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

            this.weekdayMask = this.normalizeWeekdayMask(data.weekdayMask);
            this.dayMode = this.normalizeMode(data.dayMode);
            this.dayFrom = this.normalizeDay(data.dayFrom);
            this.dayTo = this.normalizeDay(data.dayTo);
            this.monthMask = this.normalizeMonthMask(data.monthMask);
            this.yearMode = this.normalizeMode(data.yearMode);
            this.yearFrom = this.normalizeYear(data.yearFrom);
            this.yearTo = this.normalizeYear(data.yearTo);
            return;
        }

        String[] data = wiredData.split("\t");
        if (data.length != 8) {
            return;
        }

        try {
            this.weekdayMask = this.normalizeWeekdayMask(Integer.parseInt(data[0]));
            this.dayMode = this.normalizeMode(Integer.parseInt(data[1]));
            this.dayFrom = this.normalizeDay(Integer.parseInt(data[2]));
            this.dayTo = this.normalizeDay(Integer.parseInt(data[3]));
            this.monthMask = this.normalizeMonthMask(Integer.parseInt(data[4]));
            this.yearMode = this.normalizeMode(Integer.parseInt(data[5]));
            this.yearFrom = this.normalizeYear(Integer.parseInt(data[6]));
            this.yearTo = this.normalizeYear(Integer.parseInt(data[7]));
        } catch (NumberFormatException ignored) {
            this.reset();
        }
    }

    @Override
    public void onPickUp() {
        this.reset();
    }

    private void reset() {
        int currentYear = HotelDateTimeUtil.localDateNow().getYear();

        this.weekdayMask = ALL_WEEKDAYS_MASK;
        this.dayMode = MODE_SKIP;
        this.dayFrom = 1;
        this.dayTo = 31;
        this.monthMask = ALL_MONTHS_MASK;
        this.yearMode = MODE_SKIP;
        this.yearFrom = currentYear;
        this.yearTo = currentYear;
    }

    private boolean matchesMask(int value, int mask) {
        return (mask & (1 << value)) != 0;
    }

    private boolean matchesDatePart(int currentValue, int mode, int fromValue, int toValue) {
        switch (mode) {
            case MODE_EXACT:
                return currentValue == fromValue;
            case MODE_RANGE:
                return currentValue >= Math.min(fromValue, toValue) && currentValue <= Math.max(fromValue, toValue);
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

    int normalizeDay(int value) {
        return Math.max(1, Math.min(31, value));
    }

    int normalizeYear(int value) {
        return Math.max(1, Math.min(9999, value));
    }

    int normalizeWeekdayMask(int value) {
        int normalized = value & ALL_WEEKDAYS_MASK;
        return (normalized == 0) ? ALL_WEEKDAYS_MASK : normalized;
    }

    int normalizeMonthMask(int value) {
        int normalized = value & ALL_MONTHS_MASK;
        return (normalized == 0) ? ALL_MONTHS_MASK : normalized;
    }

    private static int createMask(int startValue, int endValue) {
        int mask = 0;

        for (int value = startValue; value <= endValue; value++) {
            mask |= (1 << value);
        }

        return mask;
    }

    static class JsonData {
        int weekdayMask;
        int dayMode;
        int dayFrom;
        int dayTo;
        int monthMask;
        int yearMode;
        int yearFrom;
        int yearTo;

        public JsonData(int weekdayMask, int dayMode, int dayFrom, int dayTo, int monthMask, int yearMode, int yearFrom, int yearTo) {
            this.weekdayMask = weekdayMask;
            this.dayMode = dayMode;
            this.dayFrom = dayFrom;
            this.dayTo = dayTo;
            this.monthMask = monthMask;
            this.yearMode = yearMode;
            this.yearFrom = yearFrom;
            this.yearTo = yearTo;
        }
    }
}
