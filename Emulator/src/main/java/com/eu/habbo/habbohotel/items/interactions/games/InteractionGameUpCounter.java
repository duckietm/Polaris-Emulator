package com.eu.habbo.habbohotel.items.interactions.games;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.WiredFurniStateBatch;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.rooms.items.ItemExtraDataComposer;
import com.eu.habbo.threading.runnables.games.GameUpCounter;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionGameUpCounter extends InteractionGameTimer {
    private static final int ONE_SECOND_MS = 1000;
    private static final int HALF_SECOND_MS = 500;
    /** The "base" kept in the stored "now<TAB>base" extradata, as before. */
    private static final int MAX_UPCOUNTER_TIME = ((99 * 60) + 59);
    /**
     * A counter has no visible limit: Habbo counts on past 99:59 (6000, 6001, ...) and the game does not end there
     * (observed on AIR WIN63-202610151745). Only the stored whole seconds bound it.
     */
    static final int MAX_COUNTER_SECONDS = Integer.MAX_VALUE;

    private static final int ROOM_CYCLE_MS = 500;
    private int subSecondOffsetMs = 0;
    /** The counter was adjusted while stopped: the next Start counts on from there instead of from 0. */
    private boolean adjustedSinceStop = false;
    /**
     * The counter ran since its last reset. A Set then shows at once, but the next Start counts from 0 again
     * (observed on AIR WIN63-202610201032); only a counter that never ran keeps a Set value for its Start.
     */
    private boolean ranSinceReset = false;
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
        this.setCurrentTimeMs(this.parseStoredTime() * (long) ONE_SECOND_MS);
        this.ranSinceReset = this.timeNow > 0;
        this.setExtradata(this.timeNow + "\t" + this.baseTime);
    }

    private int parseStoredTime() {
        try {
            String[] data = this.getExtradata().split("\t");

            if (data.length > 0) {
                int storedTime = Integer.parseInt(data[0]);
                return Math.max(0, storedTime);
            }
        } catch (Exception ignored) {
        }

        return Math.max(0, this.timeNow);
    }

    @Override
    protected int getInitialTimeValue() {
        return 0;
    }

    @Override
    protected boolean shouldResetTimeOnStart() {
        return this.timeNow >= MAX_COUNTER_SECONDS;
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
        this.ranSinceReset = false;
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

    /** The counter's time; past about 24 days it no longer fits and reads as {@code Integer.MAX_VALUE}. */
    public int getCurrentTimeInMs() {
        return (int) Math.min(Integer.MAX_VALUE, this.getCurrentTimeMs());
    }

    public int getMaximumTimeInMs() {
        return (int) Math.min(Integer.MAX_VALUE, this.getMaximumTimeMs());
    }

    public long getCurrentTimeMs() {
        return (this.timeNow * (long) ONE_SECOND_MS) + this.subSecondOffsetMs;
    }

    public long getMaximumTimeMs() {
        return MAX_COUNTER_SECONDS * (long) ONE_SECOND_MS;
    }

    public long getNextTickDelayMs() {
        return (this.subSecondOffsetMs > 0) ? HALF_SECOND_MS : ONE_SECOND_MS;
    }

    public void setCurrentTimeInMs(int totalMs) {
        this.setCurrentTimeMs(totalMs);
    }

    public void setCurrentTimeMs(long totalMs) {
        long clamped = Math.max(0, Math.min(totalMs, this.getMaximumTimeMs()));
        long remainder = clamped % ONE_SECOND_MS;

        this.timeNow = (int) (clamped / ONE_SECOND_MS);
        this.subSecondOffsetMs = (remainder >= HALF_SECOND_MS) ? HALF_SECOND_MS : 0;
    }

    public void advanceCounterInMs(int deltaMs) {
        this.advanceCounterMs(deltaMs);
    }

    public void advanceCounterMs(long deltaMs) {
        this.setCurrentTimeMs(this.getCurrentTimeMs() + deltaMs);
    }

    private void applyCounterState(Room room, boolean updateRoom) {
        this.setExtradata(this.timeNow + "\t" + this.baseTime);

        // Each tick goes out as an ObjectDataUpdate (id and stuff data, our "now<TAB>base" format kept) instead of a
        // full ObjectUpdate. Behaviour observed in plus-emulator/PlusEMU 3cb2aa78, reimplemented. A value written by a
        // wired firing (Start, Reset, Adjust) rides in that firing's one ObjectsDataUpdate with its box flashes,
        // once with its last value (observed on AIR WIN63-202610151745); ticks keep their own packet.
        if (updateRoom && room != null && !WiredFurniStateBatch.deferData(room, this)) {
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
        this.ranSinceReset = true;
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
        this.ranSinceReset = false;
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
        this.ranSinceReset = true;
        this.applyCounterState(room, true);
    }

    public void adjustCounter(Room room, int operator, int minutes, int halfSecondSteps) {
        long deltaMs = (Math.max(0, minutes) * 60000L) + (Math.max(0, halfSecondSteps) * (long) HALF_SECOND_MS);
        long nextTimeMs = this.getCurrentTimeMs();

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

        this.setCurrentTimeMs(nextTimeMs);
        if (!this.isRunning && !this.ranSinceReset) this.adjustedSinceStop = true;
        if (operator == 2 && this.isRunning && !this.isPaused) this.restartTickGrid();
        this.applyCounterState(room, true);

        // Setting the clock onto a time is reaching it, as in Habbo: a "clock reaches" trigger
        // for that time fires, which it never did when the time was adjusted rather than ticked to.
        if (room != null) {
            WiredManager.triggerClockCounter(room, this);
        }
    }

    /**
     * A Set on a running counter moves its tick grid onto the moment of the Set: the next step comes a full step
     * later, and a step that was due at the Set is not taken (observed on AIR WIN63-202610201032).
     */
    private void restartTickGrid() {
        this.setThreadActive(false);
        if (this.tryActivateTimerThread()) {
            this.scheduleTimerRunnable(this.getNextTickDelayMs());
        }
    }

    public void resetOnRoomUnload(Room room) {
        this.endGame(room);
        this.setThreadActive(false);
        this.setCurrentTimeInMs(0);
        this.ranSinceReset = false;
        this.applyCounterState(null, false);
    }
}
