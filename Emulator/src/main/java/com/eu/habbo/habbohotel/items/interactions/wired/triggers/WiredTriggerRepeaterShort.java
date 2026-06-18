package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredTimerInputGuard;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import gnu.trove.procedure.TObjectProcedure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WiredTriggerRepeaterShort extends WiredTriggerRepeater {
    public static final WiredTriggerType type = WiredTriggerType.PERIODICALLY_SHORT;
    public static final int STEP_MS = 50;
    public static final int DEFAULT_DELAY = 10 * STEP_MS;
    public static final int MIN_DELAY = STEP_MS;
    public static final int MAX_DELAY = 10 * STEP_MS;

    public WiredTriggerRepeaterShort(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.repeatTime = DEFAULT_DELAY;
    }

    public WiredTriggerRepeaterShort(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.repeatTime = DEFAULT_DELAY;
    }

    @Override
    public void loadWiredData(ResultSet set, Room room) throws SQLException {
        String wiredData = set.getString("wired_data");

        if (wiredData != null && wiredData.startsWith("{")) {
            JsonData data = WiredManager.getGson().fromJson(wiredData, JsonData.class);
            this.repeatTime = (data != null) ? data.repeatTime : DEFAULT_DELAY;
        } else if (wiredData != null && wiredData.length() >= 1) {
            this.repeatTime = Integer.parseInt(wiredData);
        } else {
            this.repeatTime = DEFAULT_DELAY;
        }

        this.repeatTime = clampRepeatTime(this.repeatTime);
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
        message.appendInt(this.repeatTime / STEP_MS);
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

        this.repeatTime = WiredTimerInputGuard.fromClientUnits(settings.getIntParams()[0], STEP_MS, MIN_DELAY, MAX_DELAY);

        return true;
    }

    @Override
    public void onWiredTick(Room room, long tickCount, int tickIntervalMs) {
        long elapsedMs = tickCount * tickIntervalMs;

        if (elapsedMs % this.repeatTime == 0) {
            long currentTime = System.currentTimeMillis();
            if (this.getRoomId() != 0 && room.isLoaded()
                    && WiredManager.isTriggerExecutionAllowed(room, this, currentTime)) {
                WiredManager.triggerTimerRepeatShort(room, this);
            }
        }
    }

    private int clampRepeatTime(int repeatTime) {
        if (repeatTime < MIN_DELAY) {
            return DEFAULT_DELAY;
        }

        if (repeatTime > MAX_DELAY) {
            return MAX_DELAY;
        }

        return repeatTime;
    }
}
