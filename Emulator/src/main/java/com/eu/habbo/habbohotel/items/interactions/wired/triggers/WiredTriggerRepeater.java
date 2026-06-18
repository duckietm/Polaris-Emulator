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
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Repeating wired trigger that fires periodically.
 * <p>
 * Uses the new 50ms tick system via {@link WiredTickable} for higher-resolution
 * timing compared to the old 500ms room cycle.
 * </p>
 */
public class WiredTriggerRepeater extends InteractionWiredTrigger implements WiredTickable, WiredTriggerReset {
    public static final WiredTriggerType type = WiredTriggerType.PERIODICALLY;
    public static final int DEFAULT_DELAY = 10 * 500; // 5 seconds default
    private static final int STEP_MS = 500;
    private static final int MIN_DELAY = STEP_MS;
    private static final int LEGACY_FALLBACK_DELAY = 20 * STEP_MS;

    /** The interval in milliseconds between triggers */
    protected int repeatTime = DEFAULT_DELAY;

    public WiredTriggerRepeater(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerRepeater(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean matches(HabboItem triggerItem, WiredEvent event) {
        // Only match if this repeater is the one that actually fired
        return event.getSourceItem().map(item -> item.getId() == this.getId()).orElse(false);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.repeatTime));
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        Integer storedRepeatTime = null;
        try {
            if (wiredData != null && wiredData.startsWith("{")) {
                JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
                storedRepeatTime = data != null ? data.repeatTime : null;
            } else if (wiredData != null && wiredData.length() >= 1) {
                storedRepeatTime = Integer.parseInt(wiredData);
            }
        } catch (RuntimeException ignored) {
            storedRepeatTime = null;
        }

        this.repeatTime = WiredTimerInputGuard.normalizeStoredMillis(storedRepeatTime, MIN_DELAY, LEGACY_FALLBACK_DELAY);
    }

    @Override
    public void onPickUp() {
        this.repeatTime = DEFAULT_DELAY;
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
        message.appendInt(this.repeatTime / 500);
        message.appendInt(0);
        message.appendInt(this.getType().code);

        if (!this.isTriggeredByRoomUnit()) {
            List<Integer> invalidTriggers = new ArrayList<>();
            room.getRoomSpecialTypes().getEffects(this.getX(), this.getY()).forEach(new TObjectProcedure<InteractionWiredEffect>() {
                @Override
                public boolean execute(InteractionWiredEffect object) {
                    if (object.requiresTriggeringUser()) {
                        invalidTriggers.add(object.getBaseItem().getSpriteId());
                    }
                    return true;
                }
            });
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
        this.repeatTime = WiredTimerInputGuard.fromClientUnits(settings.getIntParams()[0], STEP_MS, MIN_DELAY);

        return true;
    }

    // ========== WiredTickable Implementation ==========

    @Override
    public void onWiredTick(Room room, long tickCount, int tickIntervalMs) {
        // Calculate elapsed time based on global tick count
        // All repeaters with the same interval fire on the exact same tick
        long elapsedMs = tickCount * tickIntervalMs;
        
        // Fire when elapsed time is a multiple of repeatTime
        if (elapsedMs % this.repeatTime == 0) {
            long currentTime = System.currentTimeMillis();
            if (this.getRoomId() != 0 && room.isLoaded()
                    && WiredManager.isTriggerExecutionAllowed(room, this, currentTime)) {
                WiredManager.triggerTimerRepeat(room, this);
            }
        }
    }

    @Override
    public void resetTimer() {
        // No-op - using global tick count, no local state to reset
    }

    @Override
    public void onRegistered(Room room, long currentTimeMillis) {
        // No-op - using global tick count
    }

    @Override
    public void onUnregistered(Room room) {
        // No-op - using global tick count
    }

    @Override
    public boolean isOneShot() {
        return false; // Repeating timer
    }

    // ========== JSON Data ==========

    static class JsonData {
        int repeatTime;

        public JsonData(int repeatTime) {
            this.repeatTime = repeatTime;
        }
    }
}
