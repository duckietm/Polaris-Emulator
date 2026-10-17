package com.eu.habbo.habbohotel.items.interactions.games;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.rooms.items.ItemExtraDataComposer;
import com.eu.habbo.threading.runnables.games.GameUpCounter;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionGameUpCounter extends InteractionGameTimer {
    private static final int ONE_SECOND_MS = 1000;
    private static final int HALF_SECOND_MS = 500;
    private static final int MAX_UPCOUNTER_TIME = ((99 * 60) + 59);
    private static final int ROOM_CYCLE_MS = 500;
    private int subSecondOffsetMs = 0;
    /** The counter was adjusted while stopped: the next Start counts on from there instead of from 0. */
    private boolean adjustedSinceStop = false;
    /** The delay of the first tick of a Start, aligned to the room's 500 ms grid; 0 when none is pending. */
    private long pendingStartDelayMs = 0;

    public InteractionGameUpCounter(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.normalizeCounterState();
    }

    public InteractionGameUpCounter(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.normalizeCounterState();
    }

    @Override
    protected void parseCustomParams(Item baseItem) {
        this.TIMER_INTERVAL_STEPS = new int[] {MAX_UPCOUNTER_TIME};
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
        long pending = this.pendingStartDelayMs;
        this.pendingStartDelayMs = 0;
        if (pending > 0) return pending;
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

        // Each tick goes out as an ObjectDataUpdate (id and stuff data, our "now<TAB>base" format kept) instead of a
        // full ObjectUpdate. Behaviour observed in plus-emulator/PlusEMU 3cb2aa78, reimplemented.
        if (updateRoom && room != null) {
            room.sendComposer(new ItemExtraDataComposer(this).compose());
        }

        this.needsUpdate(true);
    }

    public void restartFromZero(Room room) {
        boolean wasActive = this.isRunning || this.isPaused;

        if (wasActive) {
            this.endGame(room);
            WiredManager.triggerGameEnds(room);
        }

        // A value adjusted after the last stop is kept, as in Habbo.
        if (!this.adjustedSinceStop) this.setCurrentTimeInMs(0);
        this.adjustedSinceStop = false;
        this.applyCounterState(room, true);

        if (room != null)
            this.pendingStartDelayMs = firstTickDelayMs(room.getCycleTimestamp(), System.currentTimeMillis());
        this.startTimer(room);
    }

    /**
     * A started counter shows its first second on the 2nd 500 ms room boundary strictly after the start, so 501 to
     * 1000 ms later, and ticks every second on that grid from then on.
     */
    static long firstTickDelayMs(long lastCycle, long now) {
        long firstBoundary = (lastCycle <= 0 || lastCycle > now)
                ? now + ROOM_CYCLE_MS
                : lastCycle + (((now - lastCycle) / ROOM_CYCLE_MS) + 1) * ROOM_CYCLE_MS;
        return firstBoundary + ROOM_CYCLE_MS - now;
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
        this.adjustedSinceStop = false;
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
        if (!this.isRunning) this.adjustedSinceStop = true;
        this.applyCounterState(room, true);

        // Setting the clock onto a time is reaching it, as in Habbo: a "clock reaches" trigger
        // for that time fires, which it never did when the time was adjusted rather than ticked to.
        if (room != null) {
            WiredManager.triggerClockCounter(room, this);
        }
    }

    public void resetOnRoomUnload(Room room) {
        this.endGame(room);
        this.setThreadActive(false);
        this.setCurrentTimeInMs(0);
        this.applyCounterState(null, false);
    }
}
