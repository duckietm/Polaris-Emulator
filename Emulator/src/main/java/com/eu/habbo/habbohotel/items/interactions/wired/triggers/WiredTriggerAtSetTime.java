package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredTimerInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredTriggerReset;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredRoomTime;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.messages.ServerMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot timer wired trigger that fires once after a set time.
 * <p>
 * Uses the new 50ms tick system via {@link WiredTickable} for accurate
 * timing. After firing, the timer automatically resets and starts again.
 * </p>
 */
public class WiredTriggerAtSetTime extends InteractionWiredTrigger implements WiredTickable, WiredTriggerReset {
    public static final WiredTriggerType type = WiredTriggerType.AT_GIVEN_TIME;
    private static final int STEP_MS = 500;
    /** 0 pulses is a setting of its own: the box fires on the first room tick after each Reset Timers only. */
    private static final int MIN_DELAY = 0;
    /** The two clocks behind the room timer and the box differ by a little; the box counts from just before. */
    private static final long JOIN_MARGIN_MS = 20L;

    private static final int LEGACY_FALLBACK_DELAY = 20 * STEP_MS;

    /** The time in milliseconds until the trigger fires */
    private final WiredRoomGridTimer gridTimer = new WiredRoomGridTimer();

    public int executeTime;

    /** Accumulated time since last reset (in milliseconds) */
    private long accumulatedTime = 0;

    /** Whether the timer has fired and is waiting for reset */
    private boolean hasFired = false;

    /** Saved, placed or moved in a loaded room: on its next tick the box joins the room timer. */
    private boolean joinRoomTimer = false;

    /** A Reset Timers ran since the box was saved or placed; a 0-pulse box fires only then. */
    private boolean resetByAction = false;

    public WiredTriggerAtSetTime(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerAtSetTime(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        // Only match if this timer is the one that actually fired
        return event.getSourceItem().map(item -> item.getId() == this.getId()).orElse(false);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.executeTime));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        Integer storedExecuteTime = null;
        try {
            if (wiredData != null && wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                storedExecuteTime = data != null ? data.executeTime : null;
            } else if (wiredData != null && wiredData.length() >= 1) {
                storedExecuteTime = Integer.parseInt(wiredData);
                // The legacy plain form predates 0 pulses, which no save could store then.
                if (storedExecuteTime == 0) storedExecuteTime = null;
            }
        } catch (RuntimeException ignored) {
            storedExecuteTime = null;
        }

        this.executeTime =
                WiredTimerInputGuard.normalizeStoredMillis(storedExecuteTime, MIN_DELAY, LEGACY_FALLBACK_DELAY);

        // Initialize for tick system - will be registered by RoomItemManager
        this.accumulatedTime = 0;
        this.hasFired = false;
    }

    @Override
    public void onPickUp() {
        this.executeTime = 0;
        this.accumulatedTime = 0;
        this.hasFired = false;
    }

    @Override
    public WiredTriggerType getType() {
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
        message.appendInt(this.executeTime / 500);
        message.appendInt(1);
        message.appendInt(this.getType().code);

        if (!this.isTriggeredByRoomUnit()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            for (InteractionWiredEffect effect : room.getRoomSpecialTypes().getEffects(this.getX(), this.getY())) {
                if (effect.requiresTriggeringUser()) {
                    invalidTriggers.add(effect.getBaseItem().getSpriteId());
                }
            }
            message.appendInt(invalidTriggers.size());
            for (Integer i : invalidTriggers) {
                message.appendInt(i);
            }
        } else {
            message.appendInt(0);
        }
    }

    @Override
    public boolean saveData(WiredSettings settings) {
        if (settings.getIntParams().length < 1) return false;
        this.executeTime = WiredTimerInputGuard.fromClientUnits(settings.getIntParams()[0], STEP_MS, MIN_DELAY);

        // A saved box counts from the room's last timer reset, not from the save (observed on AIR).
        this.accumulatedTime = 0;
        this.hasFired = false;
        this.resetByAction = false;
        this.joinRoomTimer = true;

        return true;
    }

    // ========== WiredTickable Implementation ==========

    @Override
    public void onWiredTick(Room room, long tickCount, int tickIntervalMs) {
        if (this.joinRoomTimer) {
            this.joinRoomTimer(room, System.currentTimeMillis());
        }

        // Don't tick if already fired (waiting for manual reset)
        if (this.hasFired) {
            return;
        }

        // A 0-pulse box never fires on its own save or placement, only after a Reset Timers.
        if (this.executeTime <= 0 && !this.resetByAction) {
            return;
        }

        // Counted on the room's 500 ms grid from the first room tick after the reset, as in Habbo.
        if (this.gridTimer.isDue(room.getCycleTimestamp(), System.currentTimeMillis(), this.executeTime)) {
            if (this.getRoomId() != 0 && room.isLoaded()) {
                long currentTime = System.currentTimeMillis();
                if (!WiredManager.isTriggerExecutionAllowed(room, this, currentTime)) {
                    return;
                }

                this.hasFired = true;
                this.accumulatedTime = 0;
                WiredManager.triggerTimerTick(room, this);
                return;
            }

            this.hasFired = true;
            this.accumulatedTime = 0;
        }
    }

    /**
     * Counts from the room timer's last reset: a box placed or saved after its time has passed counts as fired until
     * the next reset (observed on AIR WIN63-202610201032).
     */
    void joinRoomTimer(Room room, long now) {
        this.joinRoomTimer = false;
        long elapsed = WiredRoomTime.millisSinceTimerReset(room, now);
        this.gridTimer.reset(now - elapsed - JOIN_MARGIN_MS);
        if (this.executeTime > 0 && elapsed >= this.executeTime) {
            this.hasFired = true;
        }
    }

    @Override
    public void resetTimer() {
        this.accumulatedTime = 0;
        this.hasFired = false;
        this.joinRoomTimer = false;
        this.resetByAction = true;
        this.gridTimer.reset(System.currentTimeMillis());
    }

    @Override
    public void onRegistered(Room room, long currentTimeMillis) {
        this.accumulatedTime = 0;
        this.hasFired = false;
        this.resetByAction = false;
        // Loaded with the room it counts from the load; placed or moved in a loaded room it joins the room timer.
        this.joinRoomTimer = room != null && room.isLoaded();
        this.gridTimer.reset(currentTimeMillis);
    }

    @Override
    public void onUnregistered(Room room) {
        this.accumulatedTime = 0;
        this.hasFired = false;
    }

    @Override
    public boolean isOneShot() {
        return true; // One-shot timer, fires once then waits for reset
    }

    // ========== JSON Data ==========

    static class JsonData {
        // Boxed: a row without the field ({}) keeps the old fallback instead of reading as 0 pulses.
        Integer executeTime;

        public JsonData(int executeTime) {
            this.executeTime = executeTime;
        }
    }
}
