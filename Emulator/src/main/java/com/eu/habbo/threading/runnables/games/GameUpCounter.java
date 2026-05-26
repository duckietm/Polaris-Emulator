package com.eu.habbo.threading.runnables.games;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

public class GameUpCounter implements Runnable {
    private final InteractionGameUpCounter timer;

    public GameUpCounter(InteractionGameUpCounter timer) {
        this.timer = timer;
    }

    @Override
    public void run() {
        timer.setThreadActive(false);

        if (timer.getRoomId() == 0) {
            timer.setRunning(false);
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(timer.getRoomId());

        if (room == null || !timer.isRunning() || timer.isPaused()) {
            return;
        }

        int tickDelayMs = (int) timer.getNextTickDelayMs();
        timer.advanceCounterInMs(tickDelayMs);
        if (timer.getCurrentTimeInMs() % 1000 == 0) {
            WiredManager.triggerClockCounter(room, timer);
        }

        if (timer.getCurrentTimeInMs() < timer.getMaximumTimeInMs()) {
            if (timer.tryActivateTimerThread()) {
                Emulator.getThreading().run(this, timer.getNextTickDelayMs());
            }
        } else {
            timer.setCurrentTimeInMs(timer.getMaximumTimeInMs());
            timer.endGame(room);
            WiredManager.triggerGameEnds(room);
        }

        room.updateItem(timer);
    }
}
