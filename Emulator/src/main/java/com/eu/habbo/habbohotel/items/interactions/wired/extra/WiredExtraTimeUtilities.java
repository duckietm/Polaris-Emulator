package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredDerivedVariableBox;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

/**
 * Time-utilities add-on (furni classname {@code wf_xtra_var_time_util}). Placed on the tile of a variable
 * definition, it reads the variable as a point in time (its value as unix seconds, its creation time or its
 * last update time) and exposes sub-variables: calendar parts in the room's wired timezone and whole time
 * units counted from 1970. In the value mode the parts are writable: writing one moves the variable by the
 * difference in that part. The timestamp modes are read-only and also list the timestamp itself as
 * {@code <parent>.creation_time} / {@code <parent>.last_update_time}.
 *
 * <p>Int params {@code [mask, mode]}. Bit {@code id} of the mask selects the sub-variable with that id:
 * calendar parts 1-10, units 20-26. Our variables are 32-bit, so the value is read as unix seconds (AIR's
 * hotel stores epoch milliseconds).</p>
 */
public class WiredExtraTimeUtilities extends InteractionWiredExtra implements WiredDerivedVariableBox {
    public static final int CODE = 98;

    public static final int UNIT_MILLISECONDS = 0;
    public static final int UNIT_SECONDS = 1;
    public static final int UNIT_MINUTES = 2;
    public static final int UNIT_HOURS = 3;

    public static final int MODE_VALUE = 0;
    public static final int MODE_CREATION_TIME = 1;
    public static final int MODE_LAST_UPDATE_TIME = 2;

    public static final int SUB_MILLISECOND_OF_SECOND = 1;
    public static final int SUB_SECONDS_OF_MINUTE = 2;
    public static final int SUB_MINUTE_OF_HOUR = 3;
    public static final int SUB_HOUR_OF_DAY = 4;
    public static final int SUB_DAY_OF_WEEK = 5;
    public static final int SUB_DAY_OF_MONTH = 6;
    public static final int SUB_DAY_OF_YEAR = 7;
    public static final int SUB_WEEK_OF_YEAR = 8;
    public static final int SUB_MONTH_OF_YEAR = 9;
    public static final int SUB_YEAR = 10;
    public static final int SUB_MILLISECONDS = 20;
    public static final int SUB_SECONDS = 21;
    public static final int SUB_MINUTES = 22;
    public static final int SUB_HOURS = 23;
    public static final int SUB_DAYS = 24;
    public static final int SUB_WEEKS = 25;
    public static final int SUB_MONTHS = 26;
    /** The raw timestamp of a timestamp mode, listed as {@code <parent>.creation_time} or {@code .last_update_time}. */
    public static final int SUB_TIMESTAMP = 31;

    private static final int CALENDAR_MASK = 0x7FE;
    private static final int UNITS_MASK = 0x7F00000;
    private static final int VALID_MASK = CALENDAR_MASK | UNITS_MASK;
    private static final int MAX_SUB_ID = SUB_MONTHS;

    private int timeUnit = UNIT_SECONDS;
    private int subvariableMask = 0;
    private int mode = MODE_VALUE;

    public WiredExtraTimeUtilities(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraTimeUtilities(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] params = settings.getIntParams();
        this.subvariableMask = normalizeMask((params.length > 0) ? params[0] : 0);
        this.mode = normalizeMode((params.length > 1) ? params[1] : MODE_VALUE);
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.timeUnit, this.subvariableMask, this.mode));
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(2);
        message.appendInt(this.subvariableMask);
        message.appendInt(this.mode);
        message.appendInt(0);
        message.appendInt(CODE);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();

        String wiredData = set.getString("wired_data");
        if (wiredData == null || wiredData.isEmpty()) {
            return;
        }

        if (wiredData.startsWith("{")) {
            JsonData data = WiredExtraPayloadGuard.fromJson(wiredData, JsonData.class);
            if (data != null) {
                this.timeUnit = normalizeUnit(data.timeUnit);
                this.subvariableMask = normalizeMask(data.subvariableMask);
                this.mode = normalizeMode(data.mode);
            }
            return;
        }

        try {
            this.timeUnit = normalizeUnit(Integer.parseInt(wiredData));
        } catch (NumberFormatException ignored) {
            this.timeUnit = UNIT_SECONDS;
        }
    }

    @Override
    public void onPickUp() {
        this.timeUnit = UNIT_SECONDS;
        this.subvariableMask = 0;
        this.mode = MODE_VALUE;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    /** The unit older saves stored; nothing reads it at runtime. */
    public int getTimeUnit() {
        return this.timeUnit;
    }

    public int getSubvariableMask() {
        return this.subvariableMask;
    }

    public int getMode() {
        return this.mode;
    }

    /** Whether the sub-variables can be read from a variable that holds no value. */
    public boolean readsTimestamps() {
        return this.mode != MODE_VALUE;
    }

    @Override
    public List<Integer> getSelectedSubvariables() {
        List<Integer> result = new ArrayList<>();
        for (int id = 1; id <= MAX_SUB_ID; id++) {
            if (this.hasSubvariable(id)) result.add(id);
        }
        return result;
    }

    @Override
    public boolean hasSubvariable(int subType) {
        return subType > 0 && subType <= MAX_SUB_ID && (this.subvariableMask & (1 << subType)) != 0;
    }

    /**
     * The sub-variables this box lists: the selected parts and, in a timestamp mode, the timestamp itself.
     * Behaviour observed in plus-emulator/PlusEMU 2ec172ff, reimplemented.
     */
    public List<Integer> getListedSubvariables() {
        List<Integer> result = this.getSelectedSubvariables();
        if (this.readsTimestamps()) result.add(SUB_TIMESTAMP);
        return result;
    }

    /** Whether this box lists a sub-variable (see {@link #getListedSubvariables()}). */
    public boolean listsSubvariable(int subType) {
        return this.hasSubvariable(subType) || (subType == SUB_TIMESTAMP && this.readsTimestamps());
    }

    /** Value-mode parts are writable; timestamp parts and the timestamp itself are not. */
    public boolean isWritable(int subType) {
        return this.mode == MODE_VALUE && this.hasSubvariable(subType);
    }

    /**
     * The official name of a part: {@code <mode>.<part>}, so a variable reads as {@code name.value.hour_of_day} or
     * {@code name.creation_time.year}, with AIR's part keys (VariableTimeUtilAddon). Saved boxes keep working: they
     * refer to the parts by id, not by name. Behaviour observed in plus-emulator/PlusEMU 89f01ebf and ebf4f1fa,
     * reimplemented.
     */
    @Override
    public String subvariableKey(int subType) {
        if (subType == SUB_TIMESTAMP) return modeKey(this.mode);
        return modeKey(this.mode) + "." + partKey(subType);
    }

    /** The mode segment of the official part names. */
    public static String modeKey(int mode) {
        return switch (mode) {
            case MODE_CREATION_TIME -> "creation_time";
            case MODE_LAST_UPDATE_TIME -> "last_update_time";
            default -> "value";
        };
    }

    /** AIR's key of a part, without the mode segment. */
    public static String partKey(int subType) {
        return switch (subType) {
            case SUB_MILLISECOND_OF_SECOND -> "milliseconds_of_seconds";
            case SUB_SECONDS_OF_MINUTE -> "seconds_of_minute";
            case SUB_MINUTE_OF_HOUR -> "minute_of_hour";
            case SUB_HOUR_OF_DAY -> "hour_of_day";
            case SUB_DAY_OF_WEEK -> "day_of_week";
            case SUB_DAY_OF_MONTH -> "day_of_month";
            case SUB_DAY_OF_YEAR -> "day_of_year";
            case SUB_WEEK_OF_YEAR -> "week_of_year";
            case SUB_MONTH_OF_YEAR -> "month_of_year";
            case SUB_YEAR -> "year";
            case SUB_MILLISECONDS -> "millisecond";
            case SUB_SECONDS -> "second";
            case SUB_MINUTES -> "minute";
            case SUB_HOURS -> "hour";
            case SUB_DAYS -> "day";
            case SUB_WEEKS -> "week";
            case SUB_MONTHS -> "month";
            default -> "value";
        };
    }

    @Override
    public Integer derive(int subType, Integer baseValue) {
        return this.derive(subType, baseValue, 0, 0, ZoneOffset.UTC);
    }

    /** The sub-variable for a variable's value and timestamps (unix seconds), calendar parts in {@code zone}. */
    public Integer derive(int subType, Integer value, int createdAt, int updatedAt, ZoneId zone) {
        Long seconds = this.resolveSeconds(value, createdAt, updatedAt);
        if (seconds == null) return null;
        if (subType == SUB_TIMESTAMP) return this.readsTimestamps() ? clamp(seconds) : null;
        return deriveFromSeconds(subType, seconds, zone);
    }

    /**
     * The variable value (unix seconds) after writing {@code partValue} into a value-mode part: the variable
     * moves by the difference between the new and the current part, so 25 hours of day reads as 1 o'clock the
     * next day. Null when the part is not writable or the result does not fit the variable.
     * Behaviour observed in plus-emulator/PlusEMU ebf4f1fa, reimplemented.
     */
    public Integer write(int subType, int value, int partValue, ZoneId zone) {
        if (!this.isWritable(subType)) return null;
        Long result = writeSeconds(subType, value, partValue, (zone != null) ? zone : ZoneOffset.UTC);
        return (result != null && result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE)
                ? (int) (long) result
                : null;
    }

    static Long writeSeconds(int subType, long seconds, long partValue, ZoneId zone) {
        try {
            return switch (subType) {
                // Our variables hold seconds, so a millisecond write only moves whole seconds.
                case SUB_MILLISECOND_OF_SECOND -> Math.floorDiv(seconds * 1000L + partValue, 1000L);
                case SUB_MILLISECONDS -> Math.floorDiv(partValue, 1000L);
                case SUB_SECONDS -> partValue;
                case SUB_MINUTES -> seconds + (partValue - Math.floorDiv(seconds, 60L)) * 60L;
                case SUB_HOURS -> seconds + (partValue - Math.floorDiv(seconds, 3_600L)) * 3_600L;
                case SUB_DAYS -> seconds + (partValue - Math.floorDiv(seconds, 86_400L)) * 86_400L;
                case SUB_WEEKS -> seconds + (partValue - Math.floorDiv(seconds, 604_800L)) * 604_800L;
                case SUB_MONTHS -> {
                    ZonedDateTime utc = Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC);
                    long current = ChronoUnit.MONTHS.between(Instant.EPOCH.atZone(ZoneOffset.UTC), utc);
                    yield utc.plusMonths(partValue - current).toEpochSecond();
                }
                default -> {
                    Integer current = deriveFromSeconds(subType, seconds, zone);
                    if (current == null) yield null;
                    long delta = partValue - current;
                    ZonedDateTime local = Instant.ofEpochSecond(seconds).atZone(zone);
                    ZonedDateTime moved =
                            switch (subType) {
                                case SUB_SECONDS_OF_MINUTE -> local.plusSeconds(delta);
                                case SUB_MINUTE_OF_HOUR -> local.plusMinutes(delta);
                                case SUB_HOUR_OF_DAY -> local.plusHours(delta);
                                case SUB_DAY_OF_WEEK, SUB_DAY_OF_MONTH, SUB_DAY_OF_YEAR -> local.plusDays(delta);
                                case SUB_WEEK_OF_YEAR -> local.plusWeeks(delta);
                                case SUB_MONTH_OF_YEAR -> local.plusMonths(delta);
                                case SUB_YEAR -> local.plusYears(delta);
                                default -> null;
                            };
                    yield (moved != null) ? moved.toEpochSecond() : null;
                }
            };
        } catch (ArithmeticException | java.time.DateTimeException e) {
            return null;
        }
    }

    private Long resolveSeconds(Integer value, int createdAt, int updatedAt) {
        return switch (this.mode) {
            case MODE_CREATION_TIME -> (createdAt > 0) ? (long) createdAt : null;
            case MODE_LAST_UPDATE_TIME -> (updatedAt > 0) ? (long) updatedAt : null;
            // Values before 1970 read like any other.
            default -> (value != null) ? (long) value : null;
        };
    }

    static Integer deriveFromSeconds(int subType, long seconds, ZoneId zone) {
        if (subType >= SUB_MILLISECONDS) {
            return switch (subType) {
                case SUB_MILLISECONDS -> clamp(seconds * 1000L);
                case SUB_SECONDS -> clamp(seconds);
                case SUB_MINUTES -> clamp(Math.floorDiv(seconds, 60L));
                case SUB_HOURS -> clamp(Math.floorDiv(seconds, 3_600L));
                case SUB_DAYS -> clamp(Math.floorDiv(seconds, 86_400L));
                case SUB_WEEKS -> clamp(Math.floorDiv(seconds, 604_800L));
                // Whole months since 1970, counted towards zero (a moment late in December 1969 is month 0).
                case SUB_MONTHS ->
                    clamp(ChronoUnit.MONTHS.between(
                            Instant.EPOCH.atZone(ZoneOffset.UTC),
                            Instant.ofEpochSecond(seconds).atZone(ZoneOffset.UTC)));
                default -> null;
            };
        }

        ZonedDateTime local = Instant.ofEpochSecond(seconds).atZone((zone != null) ? zone : ZoneOffset.UTC);
        return switch (subType) {
            case SUB_MILLISECOND_OF_SECOND -> local.getNano() / 1_000_000;
            case SUB_SECONDS_OF_MINUTE -> local.getSecond();
            case SUB_MINUTE_OF_HOUR -> local.getMinute();
            case SUB_HOUR_OF_DAY -> local.getHour();
            case SUB_DAY_OF_WEEK -> local.getDayOfWeek().getValue();
            case SUB_DAY_OF_MONTH -> local.getDayOfMonth();
            case SUB_DAY_OF_YEAR -> local.getDayOfYear();
            case SUB_WEEK_OF_YEAR -> local.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case SUB_MONTH_OF_YEAR -> local.getMonthValue();
            case SUB_YEAR -> local.getYear();
            default -> null;
        };
    }

    private static int clamp(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    private static int normalizeMask(int value) {
        return value & VALID_MASK;
    }

    private static int normalizeMode(int value) {
        return (value == MODE_CREATION_TIME || value == MODE_LAST_UPDATE_TIME) ? value : MODE_VALUE;
    }

    private static int normalizeUnit(int value) {
        return Math.max(UNIT_MILLISECONDS, Math.min(UNIT_HOURS, value));
    }

    static class JsonData {
        int timeUnit = UNIT_SECONDS;
        int subvariableMask;
        int mode;

        JsonData() {}

        JsonData(int timeUnit, int subvariableMask, int mode) {
            this.timeUnit = timeUnit;
            this.subvariableMask = subvariableMask;
            this.mode = mode;
        }
    }
}
