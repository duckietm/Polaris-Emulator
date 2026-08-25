package com.eu.habbo.habbohotel.items.interactions.games;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.wired.WiredGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.threading.runnables.games.GameTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

public class InteractionGameTimer extends HabboItem {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionGameTimer.class);

    protected int[] TIMER_INTERVAL_STEPS = new int[] { 30, 60, 120, 180, 300, 600 };

    protected int baseTime = 0;
    protected int timeNow = 0;
    protected boolean isRunning = false;
    protected boolean isPaused = false;
    protected boolean threadActive = false;

    public enum InteractionGameTimerAction {
        START_STOP(1),
        INCREASE_TIME(2);

        private int action;

        InteractionGameTimerAction(int action) {
            this.action = action;
        }

        public int getAction() {
            return action;
        }

        public static InteractionGameTimerAction getByAction(int action) {
            if (action == 1) return START_STOP;
            if (action == 2) return INCREASE_TIME;

            return START_STOP;
        }
    }

    public InteractionGameTimer(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);

        parseCustomParams(baseItem);

        try {
            String[] data = set.getString("extra_data").split("\t");

            if (data.length >= 2) {
                this.baseTime = Integer.parseInt(data[1]);
                this.timeNow = this.baseTime;
            }

            if (data.length >= 1) {
                this.setExtradata(data[0] + "\t0");
            }
        }
        catch (Exception e) {
            this.baseTime = TIMER_INTERVAL_STEPS[0];
            this.timeNow = this.baseTime;
        }
    }

    public InteractionGameTimer(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        parseCustomParams(item);
    }

    protected void parseCustomParams(Item baseItem) {
        try {
            TIMER_INTERVAL_STEPS = Arrays.stream(baseItem.getCustomParams().split(","))
                    .mapToInt(s -> {
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    }).toArray();
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    public void endGame(Room room) {
        endGame(room, false);
    }

    public void endGame(Room room, boolean isStart) {
        this.isRunning = false;
        this.isPaused = false;

        for (Game game : room.getGames()) {
            if (!game.getState().equals(GameState.IDLE) && !(isStart && game instanceof WiredGame)) {
                game.onEnd();
                game.stop();
            }
        }
    }

    protected void createNewGame(Room room) {
        for(Class<? extends Game> gameClass : Emulator.getGameEnvironment().getRoomManager().getGameTypes()) {
            Game existingGame = room.getGame(gameClass);

            if (existingGame != null) {
                existingGame.initialise();
            } else {
                try {
                    Game game = gameClass.getDeclaredConstructor(Room.class).newInstance(room);
                    room.addGame(game);
                    game.initialise();
                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                }
            }
        }
    }

    protected void pause(Room room) {
        for (Game game : room.getGames()) {
            game.pause();
        }
    }

    protected void unpause(Room room) {
        for (Game game : room.getGames()) {
            game.unpause();
        }
    }

    @Override
    public void run() {
        if (this.needsUpdate() || this.needsDelete()) {
            super.run();
        }
    }

    @Override
    public void onPickUp(Room room) {
        this.endGame(room);
        this.threadActive = false;

        this.timeNow = this.getInitialTimeValue();
        this.setExtradata(this.timeNow + "\t" + this.baseTime);
        this.needsUpdate(true);
    }

    @Override
    public void onPlace(Room room) {
        if (this.baseTime < this.TIMER_INTERVAL_STEPS[0]) {
            this.baseTime = this.TIMER_INTERVAL_STEPS[0];
        }

        this.timeNow = this.getInitialTimeValue();

        this.setExtradata(this.timeNow + "\t" + this.baseTime);
        room.updateItem(this);
        this.needsUpdate(true);

        super.onPlace(room);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString("" + timeNow);

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (this.getExtradata().isEmpty()) {
            this.setExtradata("0\t" + this.TIMER_INTERVAL_STEPS[0]);
        }

        // if wired triggered it
        if (objects.length >= 2 && objects[1] instanceof WiredEffectType) {
            if(!(!this.isRunning || this.isPaused))
                return;

            boolean wasPaused = this.isPaused;
            this.endGame(room, true);

            if(wasPaused) {
                WiredManager.triggerGameEnds(room);
            }

            this.createNewGame(room);

            this.resetTimeForStart();
            this.isRunning = true;
            this.isPaused = false;

            room.updateItem(this);
            WiredManager.triggerGameStarts(room);

            if (this.tryActivateTimerThread()) {
                this.scheduleTimerRunnable(this.getTimerStartDelayMs());
            }
        } else if (client != null) {
            if (!(room.hasRights(client.getHabbo()) || client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)))
                return;

            InteractionGameTimerAction state = InteractionGameTimerAction.START_STOP;

            if (objects.length >= 1 && objects[0] instanceof Integer) {
                state = InteractionGameTimerAction.getByAction((int) objects[0]);
            }

            switch (state) {
                case START_STOP:
                    if (this.isRunning) { // a game has been started
                        this.isPaused = !this.isPaused;
                        if (this.isPaused) {
                            this.pause(room);
                        } else {
                            this.unpause(room);

                            if (this.tryActivateTimerThread()) {
                                this.scheduleTimerRunnable(this.getTimerResumeDelayMs());
                            }
                        }
                    } else {
                        this.isPaused = false;
                        this.isRunning = true;
                        this.resetTimeForStart();
                        room.updateItem(this);

                        this.createNewGame(room);
                        WiredManager.triggerGameStarts(room);

                        if (this.tryActivateTimerThread()) {
                            this.scheduleTimerRunnable(this.getTimerStartDelayMs());
                        }
                    }

                    break;

                case INCREASE_TIME:
                    if (!this.isRunning) {
                        this.increaseTimer(room);
                    } else if (this.isPaused) {
                        this.endGame(room);
                        this.increaseTimer(room);
                        WiredManager.triggerGameEnds(room);
                    }

                    break;
            }
        }

        super.onClick(client, room, objects);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }


    public void startTimer(Room room) {
        if (!isRunning) {
            isRunning = true;
            isPaused = false;
            if (this.shouldResetTimeOnStart()) {
                this.resetTimeForStart();
                room.updateItem(this);
            }
            this.createNewGame(room);
            WiredManager.triggerGameStarts(room);
            if (this.tryActivateTimerThread()) {
                this.scheduleTimerRunnable(this.getTimerStartDelayMs());
            }
        }
    }

    public void pauseTimer(Room room) {
        if (isRunning && !isPaused) {
            isPaused = true;
            pause(room);
        }
    }

    public void resumeTimer(Room room) {
        if (!this.isRunning) {
            startTimer(room);
            return;
        }

        if (this.isPaused) {
            this.isPaused = false;
            this.unpause(room);

            if (this.tryActivateTimerThread()) {
                this.scheduleTimerRunnable(this.getTimerResumeDelayMs());
            }
        }
    }

    protected void increaseTimer(Room room) {
        if (this.isRunning)
            return;

        int baseTime = -1;

        if (this.timeNow != this.baseTime) {
            baseTime = this.baseTime;
        } else {
            for (int step : this.TIMER_INTERVAL_STEPS) {
                if (this.timeNow < step) {
                    baseTime = step;
                    break;
                }
            }

            if (baseTime == -1) baseTime = this.TIMER_INTERVAL_STEPS[0];
        }

        this.baseTime = baseTime;
        this.timeNow = this.getInitialTimeValue();
        this.setExtradata(this.timeNow + "\t" + this.baseTime);
        room.updateItem(this);
        this.needsUpdate(true);
    }

    protected int getInitialTimeValue() {
        return this.baseTime;
    }

    protected boolean shouldResetTimeOnStart() {
        return this.timeNow <= 0;
    }

    protected void resetTimeForStart() {
        this.timeNow = this.baseTime;
    }

    protected Runnable createTimerRunnable() {
        return new GameTimer(this);
    }

    protected long getTimerStartDelayMs() {
        return 1000L;
    }

    protected long getTimerResumeDelayMs() {
        return 0L;
    }

    protected void scheduleTimerRunnable(long delayMs) {
        if (delayMs <= 0) {
            Emulator.getThreading().run(this.createTimerRunnable());
            return;
        }

        Emulator.getThreading().run(this.createTimerRunnable(), delayMs);
    }

    @Override
    public String getDatabaseExtraData() {
        return this.getExtradata();
    }

    @Override
    public boolean allowWiredResetState() {
        return true;
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
    }

    public void setThreadActive(boolean threadActive) {
        synchronized (this) {
            this.threadActive = threadActive;
        }
    }

    public boolean isPaused() {
        return this.isPaused;
    }

    public void reduceTime() {
        this.timeNow--;
    }

    public int getTimeNow() {
        return this.timeNow;
    }

    public void setTimeNow(int timeNow) {
        this.timeNow = timeNow;
    }

    public int getBaseTime() {
        return this.baseTime;
    }

    public boolean tryActivateTimerThread() {
        synchronized (this) {
            if (this.threadActive) {
                return false;
            }

            this.threadActive = true;
            return true;
        }
    }
}
