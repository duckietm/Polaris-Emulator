package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.rooms.WiredProjectileFlights;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import java.util.List;

/**
 * Raises Variable Changed for the projectile counts while projectiles fly, as in Habbo: on the room
 * tick after a projectile crosses into a tile its tiles traveled and collision counts are reported,
 * and its traveling flag is reported removed when it lands. Nothing is reported at launch. One watcher
 * ticks per room while a flight is in the air and lets go of itself afterwards.
 */
final class WiredProjectileWatcher implements WiredTickable {
    /** The tick service's ids are furni ids; this is not one, so it uses a seat no furni has. */
    private static final int SERVICE_ID = -1_000_000_000;
    /** The room tick the counts are looked at on. */
    static final int POLL_MS = 500;

    private final int roomId;

    private WiredProjectileWatcher(int roomId) {
        this.roomId = roomId;
    }

    /** Starts the room's watcher after a launch, unless one already runs. */
    static void ensure(Room room) {
        RoomWiredRuntime runtime = room == null ? null : room.getWiredRuntime();
        if (runtime == null || !runtime.getProjectileFlights().claimWatcher()) return;

        WiredManager.registerTickable(room, new WiredProjectileWatcher(room.getId()));
    }

    @Override
    public void onWiredTick(Room room, long tickCount, int tickIntervalMs) {
        int ticksPerPoll = Math.max(1, POLL_MS / Math.max(1, tickIntervalMs));
        if (tickCount % ticksPerPoll != 0) return;

        RoomWiredRuntime runtime = room == null ? null : room.getWiredRuntime();
        if (runtime == null) return;

        WiredProjectileFlights flights = runtime.getProjectileFlights();
        report(room, flights.poll(flights.now()));
        if (flights.releaseWatcherIfIdle()) {
            WiredManager.unregisterTickable(room, this);
        }
    }

    static void report(Room room, List<WiredProjectileFlights.Change> changes) {
        for (WiredProjectileFlights.Change change : changes) {
            HabboItem item = room.getHabboItem(change.itemId());
            if (item == null) continue;

            boolean created = change.before() == null;
            boolean deleted = change.after() == null;
            WiredEvent.VariableChangeKind kind = created || deleted
                    ? WiredEvent.VariableChangeKind.NONE
                    : change.after() > change.before()
                            ? WiredEvent.VariableChangeKind.INCREASED
                            : WiredEvent.VariableChangeKind.DECREASED;
            WiredManager.handleEvent(WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                    .sourceItem(item)
                    .variableTargetType(1)
                    .internalVariableKey(change.key())
                    .variableCreated(created)
                    .variableDeleted(deleted)
                    .variableChangeKind(kind)
                    .variableValues(
                            change.before() == null ? 0L : change.before(),
                            change.after() == null ? 0L : change.after())
                    .build());
        }
    }

    @Override
    public void resetTimer() {}

    @Override
    public int getId() {
        return SERVICE_ID - this.roomId;
    }

    @Override
    public int getRoomId() {
        return this.roomId;
    }
}
