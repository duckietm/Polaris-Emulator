package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import java.util.ArrayList;
import java.util.List;

public class RoomUnitWalkToLocation implements Runnable {
    private RoomUnit walker;
    private RoomTile goalTile;
    private Room room;
    private List<Runnable> targetReached;
    private List<Runnable> failedReached;

    public RoomUnitWalkToLocation(
            RoomUnit walker, RoomTile goalTile, Room room, Runnable targetReached, Runnable failedReached) {
        this.walker = walker;
        this.goalTile = goalTile;
        this.room = room;

        this.targetReached = new ArrayList<>();
        if (targetReached != null) this.targetReached.add(targetReached);

        this.failedReached = new ArrayList<>();
        // It went into targetReached, so a failed walk never ran it and a successful one ran it as well.
        if (failedReached != null) this.failedReached.add(failedReached);
    }

    public RoomUnitWalkToLocation(
            RoomUnit walker, RoomTile goalTile, Room room, List<Runnable> targetReached, List<Runnable> failedReached) {
        this.walker = walker;
        this.goalTile = goalTile;
        this.room = room;
        this.targetReached = targetReached;
        this.failedReached = failedReached;
    }

    @Override
    public void run() {
        if (this.goalTile == null
                || this.walker == null
                || this.room == null
                || this.walker.getRoom() == null
                || this.walker.getRoom().getId() != this.room.getId()) {
            onFail();
            return;
        }

        if (this.walker.getCurrentLocation().equals(this.goalTile)) {
            onSuccess();
            return;
        }

        RoomTile currentGoal = this.walker.getGoal();
        if (currentGoal == null
                || !currentGoal.equals(this.goalTile)
                || (this.walker.getPath().size() == 0 && !this.walker.hasStatus(RoomUnitStatus.MOVE))) {
            onFail();
            return;
        }

        Emulator.getThreading().run(this, 250);
    }

    private void onSuccess() {
        for (Runnable r : this.targetReached) Emulator.getThreading().run(r);
    }

    private void onFail() {
        for (Runnable r : this.failedReached) Emulator.getThreading().run(r);
    }
}
