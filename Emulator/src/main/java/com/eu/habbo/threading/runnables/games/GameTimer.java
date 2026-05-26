package com.eu.habbo.threading.runnables.games;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

public class GameTimer implements Runnable {

    private final InteractionGameTimer timer;

    public GameTimer(InteractionGameTimer timer) {
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

        timer.reduceTime();
        if (timer.getTimeNow() < 0) timer.setTimeNow(0);

        if (timer.getTimeNow() > 0) {
            if (timer.tryActivateTimerThread()) {
                Emulator.getThreading().run(this, 1000);
            }
        } else {
            timer.setTimeNow(0);
            timer.endGame(room);
            WiredManager.triggerGameEnds(room);
        }

        room.updateItem(timer);
    }
}
