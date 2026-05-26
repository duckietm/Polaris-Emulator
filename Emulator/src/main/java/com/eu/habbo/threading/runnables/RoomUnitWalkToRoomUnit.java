package com.eu.habbo.threading.runnables;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

import java.util.List;

public class RoomUnitWalkToRoomUnit implements Runnable {
    private final int minDistance;
    private RoomUnit walker;
    private RoomUnit target;
    private Room room;
    private List<Runnable> targetReached;
    private List<Runnable> failedReached;

    private RoomTile goalTile = null;

    public RoomUnitWalkToRoomUnit(RoomUnit walker, RoomUnit target, Room room, List<Runnable> targetReached, List<Runnable> failedReached) {
        this.walker = walker;
        this.target = target;
        this.room = room;
        this.targetReached = targetReached;
        this.failedReached = failedReached;
        this.minDistance = 1;
    }

    public RoomUnitWalkToRoomUnit(RoomUnit walker, RoomUnit target, Room room, List<Runnable> targetReached, List<Runnable> failedReached, int minDistance) {
        this.walker = walker;
        this.target = target;
        this.room = room;
        this.targetReached = targetReached;
        this.failedReached = failedReached;
        this.minDistance = minDistance;
    }

    @Override
    public void run() {
        if (this.room == null || !this.room.isLoaded() || this.walker == null || this.target == null) {
            return;
        }

        if (this.goalTile == null) {
            this.findNewLocation();
            Emulator.getThreading().run(this, 500);
            return;
        }

        if (this.walker.getGoal().equals(this.goalTile)) { // check that the action hasn't been cancelled by changing the goal
            if (this.walker.getCurrentLocation().distance(this.goalTile) <= this.minDistance) {
                for (Runnable r : this.targetReached) {
                    Emulator.getThreading().run(r);
                }
                WiredManager.triggerBotReachedHabbo(this.room, this.walker, this.target);
            } else {
                Emulator.getThreading().run(this, 500);
            }
        }
    }

    private void findNewLocation() {
        this.goalTile = this.walker.getClosestAdjacentTile(this.target.getCurrentLocation().x, this.target.getCurrentLocation().y, true);

        if (this.goalTile == null) {
            if (this.failedReached != null) {
                for (Runnable r : this.failedReached) {
                    Emulator.getThreading().run(r);
                }
            }

            return;
        }

        this.walker.setGoalLocation(this.goalTile);

        if (this.walker.getPath().isEmpty() && this.failedReached != null) {
            for (Runnable r : this.failedReached) {
                Emulator.getThreading().run(r);
            }
        }
    }
}
