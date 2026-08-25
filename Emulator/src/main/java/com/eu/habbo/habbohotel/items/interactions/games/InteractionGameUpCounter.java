package com.eu.habbo.habbohotel.items.interactions.games;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.threading.runnables.games.GameUpCounter;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionGameUpCounter extends InteractionGameTimer {
    private static final int ONE_SECOND_MS = 1000;
    private static final int HALF_SECOND_MS = 500;
    private static final int MAX_UPCOUNTER_TIME = ((99 * 60) + 59);
    private int subSecondOffsetMs = 0;

    public InteractionGameUpCounter(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.normalizeCounterState();
    }

    public InteractionGameUpCounter(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.normalizeCounterState();
    }

    @Override
    protected void parseCustomParams(Item baseItem) {
        this.TIMER_INTERVAL_STEPS = new int[] { MAX_UPCOUNTER_TIME };
    }

    private void normalizeCounterState() {
        this.baseTime = MAX_UPCOUNTER_TIME;
        this.setCurrentTimeInMs(this.parseStoredTime() * ONE_SECOND_MS);
        this.setExtradata(this.timeNow + "\t" + this.baseTime);
    }

    private int parseStoredTime() {
        try {
            String[] data = this.getExtradata().split("\t");

            if (data.length > 0) {
                int storedTime = Integer.parseInt(data[0]);
                return Math.max(0, Math.min(storedTime, this.baseTime));
            }
        } catch (Exception ignored) {
        }

        return Math.max(0, Math.min(this.timeNow, this.baseTime));
    }

    @Override
    protected int getInitialTimeValue() {
        return 0;
    }

    @Override
    protected boolean shouldResetTimeOnStart() {
        return this.timeNow >= this.baseTime;
    }

    @Override
    protected void resetTimeForStart() {
        this.setCurrentTimeInMs(0);
    }

    @Override
    protected void increaseTimer(Room room) {
        if (this.isRunning && !this.isPaused) {
            return;
        }

        if (this.isRunning) {
            this.endGame(room);
            WiredManager.triggerGameEnds(room);
        }

        this.baseTime = MAX_UPCOUNTER_TIME;
        this.setCurrentTimeInMs(0);
        this.applyCounterState(room, true);
    }

    @Override
    protected Runnable createTimerRunnable() {
        return new GameUpCounter(this);
    }

    @Override
    protected long getTimerStartDelayMs() {
        return this.getNextTickDelayMs();
    }

    @Override
    protected long getTimerResumeDelayMs() {
        return this.getNextTickDelayMs();
    }

    public int getCurrentTimeInMs() {
        return (this.timeNow * ONE_SECOND_MS) + this.subSecondOffsetMs;
    }

    public int getMaximumTimeInMs() {
        return this.baseTime * ONE_SECOND_MS;
    }

    public long getNextTickDelayMs() {
        return (this.subSecondOffsetMs > 0) ? HALF_SECOND_MS : ONE_SECOND_MS;
    }

    public void setCurrentTimeInMs(int totalMs) {
        int clamped = Math.max(0, Math.min(totalMs, this.getMaximumTimeInMs()));
        int remainder = clamped % ONE_SECOND_MS;

        this.timeNow = (clamped / ONE_SECOND_MS);
        this.subSecondOffsetMs = (remainder >= HALF_SECOND_MS) ? HALF_SECOND_MS : 0;
    }

    public void advanceCounterInMs(int deltaMs) {
        this.setCurrentTimeInMs(this.getCurrentTimeInMs() + deltaMs);
    }

    private void applyCounterState(Room room, boolean updateRoom) {
        this.setExtradata(this.timeNow + "\t" + this.baseTime);

        if (updateRoom && room != null) {
            room.updateItem(this);
        }

        this.needsUpdate(true);
    }

    public void restartFromZero(Room room) {
        boolean wasActive = this.isRunning || this.isPaused;

        if (wasActive) {
            this.endGame(room);
            WiredManager.triggerGameEnds(room);
        }

        this.setCurrentTimeInMs(0);
        this.applyCounterState(room, true);

        this.startTimer(room);
    }

    public void stopCounter(Room room) {
        boolean wasActive = this.isRunning || this.isPaused;

        this.endGame(room);
        this.applyCounterState(room, true);

        if (wasActive) {
            WiredManager.triggerGameEnds(room);
        }
    }

    public void resetCounter(Room room) {
        boolean wasActive = this.isRunning || this.isPaused;

        this.endGame(room);
        this.setCurrentTimeInMs(0);
        this.applyCounterState(room, true);

        if (wasActive) {
            WiredManager.triggerGameEnds(room);
        }
    }

    public void pauseCounter(Room room) {
        if (!this.isRunning || this.isPaused) {
            return;
        }

        this.pauseTimer(room);
        this.applyCounterState(room, true);
    }

    public void resumeCounter(Room room) {
        if (!this.isPaused) {
            return;
        }

        this.resumeTimer(room);
        this.applyCounterState(room, true);
    }

    public void adjustCounter(Room room, int operator, int minutes, int halfSecondSteps) {
        int deltaMs = (Math.max(0, minutes) * 60000) + (Math.max(0, halfSecondSteps) * HALF_SECOND_MS);
        int nextTimeMs = this.getCurrentTimeInMs();

        switch (operator) {
            case 0:
                nextTimeMs += deltaMs;
                break;
            case 1:
                nextTimeMs -= deltaMs;
                break;
            case 2:
            default:
                nextTimeMs = deltaMs;
                break;
        }

        this.setCurrentTimeInMs(nextTimeMs);
        this.applyCounterState(room, true);
    }

    public void resetOnRoomUnload(Room room) {
        this.endGame(room);
        this.setThreadActive(false);
        this.setCurrentTimeInMs(0);
        this.applyCounterState(null, false);
    }
}
