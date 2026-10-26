package com.eu.habbo.threading.runnables.games;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

public class GameUpCounter implements Runnable {
    private final InteractionGameUpCounter timer;

    private int chain;

    public GameUpCounter(InteractionGameUpCounter timer) {
        this.timer = timer;
        this.chain = timer.getTimerChain();
    }

    @Override
    public void run() {
        if (!timer.releaseTimerThread(this.chain)) {
            return;
        }

        if (timer.getRoomId() == 0) {
            timer.setRunning(false);
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(timer.getRoomId());

        if (room == null || !timer.isRunning() || timer.isPaused()) {
            return;
        }

        long tickDelayMs = timer.getNextTickDelayMs();
        timer.advanceCounterMs(tickDelayMs);
        // Every tick, whole or half second: a trigger set to X.5 seconds used to wait forever.
        WiredManager.triggerClockCounter(room, timer);

        if (timer.getCurrentTimeMs() < timer.getMaximumTimeMs()) {
            if (timer.tryActivateTimerThread()) {
                this.chain = timer.getTimerChain();
                Emulator.getThreading().run(this, timer.getNextTickDelayMs());
            }
        } else {
            timer.setCurrentTimeMs(timer.getMaximumTimeMs());
            timer.endGame(room);
            WiredManager.triggerGameEnds(room);
        }

        room.updateItem(timer);
    }
}
