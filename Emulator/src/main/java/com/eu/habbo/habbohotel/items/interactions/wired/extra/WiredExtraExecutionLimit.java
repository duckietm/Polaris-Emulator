package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;

public class WiredExtraExecutionLimit extends InteractionWiredExtra {
    public static final int CODE = 65;
    public static final int MIN_EXECUTIONS = 1;
    public static final int MAX_EXECUTIONS = 100;
    public static final int DEFAULT_EXECUTIONS = 1;
    public static final int MIN_TIME_WINDOW_MS = 1000;
    public static final int MAX_TIME_WINDOW_MS = 10000;
    public static final int DEFAULT_TIME_WINDOW_MS = 1000;
    public static final int TIME_WINDOW_STEP_MS = 500;

    private final Deque<Long> recentExecutionTimestamps = new ArrayDeque<>();
    private int maxExecutions = DEFAULT_EXECUTIONS;
    private int timeWindowMs = DEFAULT_TIME_WINDOW_MS;

    public WiredExtraExecutionLimit(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraExecutionLimit(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public boolean saveData(WiredSettings settings, GameClient gameClient) {
        int[] intParams = settings.getIntParams();
        int nextExecutions = (intParams.length > 0) ? intParams[0] : this.maxExecutions;
        int nextTimeWindowMs = (intParams.length > 1) ? intParams[1] : this.timeWindowMs;

        this.maxExecutions = normalizeExecutions(nextExecutions);
        this.timeWindowMs = normalizeTimeWindowMs(nextTimeWindowMs);
        clearRuntimeState();
        return true;
    }

    @Override
    public String getWiredData() {
        return WiredManager.getGson().toJson(new JsonData(this.maxExecutions, this.timeWindowMs));
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
        message.appendInt(this.maxExecutions);
        message.appendInt(this.timeWindowMs);
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
                this.maxExecutions = normalizeExecutions(data.maxExecutions);
                this.timeWindowMs = normalizeTimeWindowMs(data.timeWindowMs);
            }

            return;
        }

        String[] legacyData = wiredData.split(";");

        try {
            if (legacyData.length > 0) {
                this.maxExecutions = normalizeExecutions(Integer.parseInt(legacyData[0]));
            }

            if (legacyData.length > 1) {
                this.timeWindowMs = normalizeTimeWindowMs(Integer.parseInt(legacyData[1]));
            }
        } catch (NumberFormatException ignored) {
            this.maxExecutions = DEFAULT_EXECUTIONS;
            this.timeWindowMs = DEFAULT_TIME_WINDOW_MS;
        }
    }

    @Override
    public void onPickUp() {
        this.maxExecutions = DEFAULT_EXECUTIONS;
        this.timeWindowMs = DEFAULT_TIME_WINDOW_MS;
        clearRuntimeState();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onMove(Room room, RoomTile oldLocation, RoomTile newLocation) {
        super.onMove(room, oldLocation, newLocation);
        clearRuntimeState();
    }

    @Override
    public boolean hasConfiguration() {
        return true;
    }

    public boolean tryAcquireExecutionSlot(long timestamp) {
        synchronized (this.recentExecutionTimestamps) {
            pruneExpiredTimestamps(timestamp);

            if (this.recentExecutionTimestamps.size() >= this.maxExecutions) {
                return false;
            }

            this.recentExecutionTimestamps.addLast(timestamp);
            return true;
        }
    }

    public boolean canExecuteAt(long timestamp) {
        synchronized (this.recentExecutionTimestamps) {
            pruneExpiredTimestamps(timestamp);
            return this.recentExecutionTimestamps.size() < this.maxExecutions;
        }
    }

    public int getMaxExecutions() {
        return this.maxExecutions;
    }

    public int getTimeWindowMs() {
        return this.timeWindowMs;
    }

    public void clearRuntimeState() {
        synchronized (this.recentExecutionTimestamps) {
            this.recentExecutionTimestamps.clear();
        }
    }

    private void pruneExpiredTimestamps(long timestamp) {
        while (!this.recentExecutionTimestamps.isEmpty()
                && (timestamp - this.recentExecutionTimestamps.peekFirst()) >= this.timeWindowMs) {
            this.recentExecutionTimestamps.removeFirst();
        }
    }

    private static int normalizeExecutions(int value) {
        return Math.max(MIN_EXECUTIONS, Math.min(MAX_EXECUTIONS, value));
    }

    private static int normalizeTimeWindowMs(int value) {
        if (value < MIN_TIME_WINDOW_MS) {
            return MIN_TIME_WINDOW_MS;
        }

        if (value > MAX_TIME_WINDOW_MS) {
            return MAX_TIME_WINDOW_MS;
        }

        return Math.round(value / (float) TIME_WINDOW_STEP_MS) * TIME_WINDOW_STEP_MS;
    }

    static class JsonData {
        int maxExecutions;
        int timeWindowMs;

        JsonData(int maxExecutions, int timeWindowMs) {
            this.maxExecutions = maxExecutions;
            this.timeWindowMs = timeWindowMs;
        }
    }
}
