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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * "Daily trigger" ({@code wf_cnd_daily_trg}): the triggering user passes once per calendar day, on
 * the room's wired clock. The day each user last passed is stored with the box; picking it up forgets
 * everyone. No settings.
 */
public class WiredConditionUserDaily extends InteractionWiredCondition {
    public static final WiredConditionType type = WiredConditionType.USER_DAILY;

    private final Map<Integer, Long> lastPassDay = new LinkedHashMap<>();
    private LongSupplier dayClock;

    public WiredConditionUserDaily(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserDaily(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    void dayClock(LongSupplier dayClock) {
        this.dayClock = dayClock;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Integer userId = WiredConditionUserCooldown.triggeringUserId(ctx);
        if (userId == null) return false;

        long today = this.today(ctx);
        synchronized (this.lastPassDay) {
            Long last = this.lastPassDay.get(userId);
            return last == null || last != today;
        }
    }

    /** The day is spent only when the stack runs: a stack that failed on another condition leaves it for later. */
    @Override
    public void commit(WiredContext ctx) {
        Integer userId = WiredConditionUserCooldown.triggeringUserId(ctx);
        if (userId == null) return;

        long today = this.today(ctx);
        synchronized (this.lastPassDay) {
            this.lastPassDay.remove(userId);
            this.lastPassDay.put(userId, today);
            WiredConditionUserCooldown.trimOldest(this.lastPassDay);
        }
        this.needsUpdate(true);
    }

    private long today(WiredContext ctx) {
        return (this.dayClock != null)
                ? this.dayClock.getAsLong()
                : WiredRoomTime.now(ctx.room()).toLocalDate().toEpochDay();
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        this.setExtradata("");
        this.needsUpdate(true);
        return true;
    }

    @Override
    public String getWiredData() {
        synchronized (this.lastPassDay) {
            return WiredManager.getGson().toJson(new JsonData(new LinkedHashMap<>(this.lastPassDay)));
        }
    }

    @Override
    public void serializeWiredData(ServerMessage message, Room room) {
        message.appendBoolean(false);
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getBaseItem().getSpriteId());
        message.appendInt(this.getId());
        message.appendString("");
        message.appendInt(0);
        message.appendInt(0);
        message.appendInt(this.getType().code);
        message.appendInt(0);
        message.appendInt(0);
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        this.onPickUp();
        String wiredData = set.getString("wired_data");
        if (wiredData == null || !wiredData.startsWith("{")) return;

        JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
        if (data == null || data.days == null) return;

        synchronized (this.lastPassDay) {
            for (Map.Entry<Integer, Long> entry : data.days.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null)
                    this.lastPassDay.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void onPickUp() {
        synchronized (this.lastPassDay) {
            this.lastPassDay.clear();
        }
        this.setExtradata("");
    }

    static class JsonData {
        Map<Integer, Long> days;

        JsonData(Map<Integer, Long> days) {
            this.days = days;
        }
    }
}
