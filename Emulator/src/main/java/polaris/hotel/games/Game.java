package polaris.hotel.games;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.items.interactions.InteractionWiredHighscore;
import polaris.hotel.items.interactions.games.InteractionGameTimer;
import polaris.hotel.items.interactions.wired.extra.WiredBlob;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.highscores.WiredHighscoreDataEntry;
import polaris.messages.outgoing.guides.GuideSessionPartnerIsPlayingComposer;
import polaris.plugin.Event;
import polaris.plugin.events.games.GameStartedEvent;
import polaris.plugin.events.games.GameStoppedEvent;
import polaris.plugin.events.games.GameUserJoinEvent;
import polaris.plugin.events.games.GameUserLeaveEvent;
import polaris.threading.runnables.SaveScoreForTeam;

public abstract class Game implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Game.class);
    // Concurrent: several paths iterate the teams without the lock while players join and leave.
    protected final Map<GameTeamColors, GameTeam> teams = new ConcurrentHashMap<>();
    protected final Room room;
    private final Class<? extends GameTeam> gameTeamClazz;
    private final Class<? extends GamePlayer> gamePlayerClazz;
    private final boolean countsAchievements;
    public boolean isRunning;
    public GameState state = GameState.IDLE;
    private int startTime;
    private int endTime;

    /**
     * Which run() chain is the live one.
     *
     * <p>A game re-schedules itself ({@code run(this, 1000)}) and stops only when it finds
     * the state IDLE. stop() sets IDLE, but a tick scheduled before that is already in the
     * queue: if initialise() and start() come first - a wired control clock sending STOP
     * then START, which a repeater can do twice a second - that tick wakes to a RUNNING
     * game and carries on, while start() has begun a chain of its own. The chains add up
     * without limit, each one cycling the game again per second, until the pool is full.
     *
     * <p>start() bumps this, and a tick scheduled by an older chain exits instead of
     * running.
     */
    private final java.util.concurrent.atomic.AtomicInteger generation =
            new java.util.concurrent.atomic.AtomicInteger();

    private static polaris.threading.ThreadPooling threading() {
        return Emulator.getThreading();
    }

    public Game(
            Class<? extends GameTeam> gameTeamClazz,
            Class<? extends GamePlayer> gamePlayerClazz,
            Room room,
            boolean countsAchievements) {
        this.gameTeamClazz = gameTeamClazz;
        this.gamePlayerClazz = gamePlayerClazz;
        this.room = room;
        this.countsAchievements = countsAchievements;
    }

    public abstract void initialise();

    public boolean addUser(User user, GameTeamColors teamColor) {
        try {
            if (user != null) {
                if (Emulator.getPluginManager().isRegistered(GameUserJoinEvent.class, true)) {
                    Event gameUserJoinEvent = new GameUserJoinEvent(this, user);
                    Emulator.getPluginManager().fireEvent(gameUserJoinEvent);
                    if (gameUserJoinEvent.isCancelled()) return false;
                }

                synchronized (this.teams) {
                    GameTeam team = this.getTeam(teamColor);
                    if (team == null) {
                        team = this.gameTeamClazz
                                .getDeclaredConstructor(GameTeamColors.class)
                                .newInstance(teamColor);
                        this.addTeam(team);
                    }

                    GamePlayer player = this.gamePlayerClazz
                            .getDeclaredConstructor(User.class, GameTeamColors.class)
                            .newInstance(user, teamColor);
                    team.addMember(player);
                    user.getUserInfo().setCurrentGame(this.getClass());
                    user.getUserInfo().setGamePlayer(player);
                }
                user.getClient().sendResponse(new GuideSessionPartnerIsPlayingComposer(true));
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return false;
    }

    public void removeUser(User user) {
        if (user != null) {
            if (Emulator.getPluginManager().isRegistered(GameUserLeaveEvent.class, true)) {
                Event gameUserLeaveEvent = new GameUserLeaveEvent(this, user);
                Emulator.getPluginManager().fireEvent(gameUserLeaveEvent);
                if (gameUserLeaveEvent.isCancelled()) return;
            }

            GameTeam team = this.getTeamForUser(user);
            if (team != null && team.isMember(user)) {
                if (user.getUserInfo().getGamePlayer() != null) {
                    team.removeMember(user.getUserInfo().getGamePlayer());
                    if (user.getUserInfo().getGamePlayer() != null) {
                        user.getUserInfo().getGamePlayer().reset();
                    }
                }

                user.getUserInfo().setCurrentGame(null);
                user.getUserInfo().setGamePlayer(null);
                user.getClient().sendResponse(new GuideSessionPartnerIsPlayingComposer(false));
                if (this.countsAchievements && this.endTime > this.startTime) {
                    AchievementManager.progressAchievement(
                            user,
                            Emulator.getGameEnvironment()
                                    .getAchievementManager()
                                    .getAchievement("GamePlayed"));
                }
            }
        }
    }

    public void start() {
        this.isRunning = false;
        this.generation.incrementAndGet();
        this.state = GameState.RUNNING;
        this.startTime = Emulator.getIntUnixTimestamp();

        if (Emulator.getPluginManager().isRegistered(GameStartedEvent.class, true)) {
            Event gameStartedEvent = new GameStartedEvent(this);
            Emulator.getPluginManager().fireEvent(gameStartedEvent);
        }

        for (UserItem item : this.room.getRoomSpecialTypes().getItemsOfType(WiredBlob.class)) {
            ((WiredBlob) item).onGameStart(this.room);
        }

        for (GameTeam team : this.teams.values()) {
            team.resetScores();
        }
    }

    /** The points the players scored themselves, without what wired boxes gave them. */
    static int playedPoints(Collection<GameTeam> teams) {
        int points = 0;
        for (GameTeam team : teams) {
            for (GamePlayer player : team.getMembers()) {
                points += Math.max(0, player.getScoreAchievementValue());
            }
        }
        return points;
    }

    public void onEnd() {
        this.endTime = Emulator.getIntUnixTimestamp();

        this.saveScores();

        int totalPointsGained =
                this.teams.values().stream().mapToInt(GameTeam::getTotalScore).sum();

        // The author's experience counts the points players scored themselves: the team total also holds
        // what Give Score boxes handed out, so an owner playing alone with a repeater earned unbounded
        // achievement progress.
        User roomOwner = Emulator.getGameEnvironment().getUserManager().getUser(this.room.getOwnerId());
        int playedPoints = playedPoints(this.teams.values());
        if (roomOwner != null && playedPoints > 0) {
            AchievementManager.progressAchievement(
                    roomOwner,
                    Emulator.getGameEnvironment().getAchievementManager().getAchievement("GameAuthorExperience"),
                    playedPoints);
        }

        GameTeam winningTeam = null;
        if (totalPointsGained > 0) {
            for (GameTeam team : this.teams.values()) {
                if (winningTeam == null || team.getTotalScore() > winningTeam.getTotalScore()) {
                    winningTeam = team;
                }
            }
        }

        if (winningTeam != null) {
            for (GamePlayer player : winningTeam.getMembers()) {
                WiredManager.triggerTeamWins(this.room, player.getUser().getRoomUnit());

                User winner = player.getUser();
                if (winner != null) {
                    // The winner's experience, which went to the room owner instead.
                    AchievementManager.progressAchievement(
                            winner,
                            Emulator.getGameEnvironment()
                                    .getAchievementManager()
                                    .getAchievement("GamePlayerExperience"));
                }
            }

            if (winningTeam.getMembers().size() > 0) {
                for (UserItem item : this.room.getRoomSpecialTypes().getItemsOfType(InteractionWiredHighscore.class)) {
                    Emulator.getGameEnvironment()
                            .getItemManager()
                            .getHighscoreManager()
                            .addHighscoreData(new WiredHighscoreDataEntry(
                                    item.getId(),
                                    winningTeam.getMembers().stream()
                                            .map(m -> m.getUser().getUserInfo().getId())
                                            .collect(Collectors.toList()),
                                    winningTeam.getTotalScore(),
                                    true,
                                    Emulator.getIntUnixTimestamp()));
                }
            }

            for (GameTeam team : this.teams.values()) {
                if (team == winningTeam) continue;

                for (GamePlayer player : team.getMembers()) {
                    WiredManager.triggerTeamLoses(this.room, player.getUser().getRoomUnit());
                }

                if (team.getMembers().size() > 0 && team.getTotalScore() > 0) {
                    for (UserItem item :
                            this.room.getRoomSpecialTypes().getItemsOfType(InteractionWiredHighscore.class)) {
                        Emulator.getGameEnvironment()
                                .getItemManager()
                                .getHighscoreManager()
                                .addHighscoreData(new WiredHighscoreDataEntry(
                                        item.getId(),
                                        team.getMembers().stream()
                                                .map(m -> m.getUser()
                                                        .getUserInfo()
                                                        .getId())
                                                .collect(Collectors.toList()),
                                        team.getTotalScore(),
                                        false,
                                        Emulator.getIntUnixTimestamp()));
                    }
                }
            }
        }

        for (UserItem item : this.room.getRoomSpecialTypes().getItemsOfType(InteractionWiredHighscore.class)) {
            ((InteractionWiredHighscore) item).reloadData();
            this.room.updateItem(item);
        }

        for (UserItem item : this.room.getRoomSpecialTypes().getItemsOfType(WiredBlob.class)) {
            ((WiredBlob) item).onGameEnd(this.room);
        }
    }

    public abstract void run();

    public void pause() {
        if (this.state.equals(GameState.RUNNING)) {
            this.state = GameState.PAUSED;
        }
    }

    public void unpause() {
        if (this.state.equals(GameState.PAUSED)) {
            this.state = GameState.RUNNING;
        }
    }

    /**
     * Schedules this game's next tick, as part of the chain the caller belongs to.
     *
     * <p>A tick whose chain has been replaced by a later start() is dropped, so only one
     * chain is ever live. Use this instead of scheduling the game directly.
     */
    protected void scheduleTick(int millis) {
        int chain = this.generation.get();
        threading()
                .run(
                        () -> {
                            if (this.generation.get() != chain) {
                                return;
                            }
                            this.run();
                        },
                        millis);
    }

    public void stop() {
        this.state = GameState.IDLE;

        boolean gamesActive = false;
        for (InteractionGameTimer timer :
                room.getRoomSpecialTypes().getGameTimers().values()) {
            if (timer.isRunning()) {
                gamesActive = true;
                break;
            }
        }

        if (gamesActive) {
            return;
        }

        if (Emulator.getPluginManager().isRegistered(GameStoppedEvent.class, true)) {
            Event gameStoppedEvent = new GameStoppedEvent(this);
            Emulator.getPluginManager().fireEvent(gameStoppedEvent);
        }
    }

    public void dispose() {
        for (GameTeam team : this.teams.values()) {
            team.clearMembers();
        }
        this.teams.clear();

        this.stop();
    }

    private void saveScores() {
        if (this.room == null) return;

        Map<GameTeamColors, GameTeam> teamsCopy = new HashMap<>();
        teamsCopy.putAll(this.teams);

        for (Map.Entry<GameTeamColors, GameTeam> teamEntry : teamsCopy.entrySet()) {
            threading().runPersistence(new SaveScoreForTeam(teamEntry.getValue(), this));
        }
    }

    public GameTeam getTeamForUser(User user) {
        if (user != null) {
            synchronized (this.teams) {
                for (GameTeam team : this.teams.values()) {
                    if (team.isMember(user)) {
                        return team;
                    }
                }
            }
        }

        return null;
    }

    public GameTeam getTeam(GameTeamColors teamColor) {
        synchronized (this.teams) {
            return this.teams.get(teamColor);
        }
    }

    public void addTeam(GameTeam team) {
        synchronized (this.teams) {
            this.teams.put(team.teamColor, team);
        }
    }

    public Room getRoom() {
        return this.room;
    }

    public int getStartTime() {
        return this.startTime;
    }

    public Class<? extends GameTeam> getGameTeamClass() {
        return gameTeamClazz;
    }

    public Class<? extends GamePlayer> getGamePlayerClass() {
        return gamePlayerClazz;
    }

    public Map<GameTeamColors, GameTeam> getTeams() {
        return teams;
    }

    public boolean isCountsAchievements() {
        return countsAchievements;
    }

    public GameState getState() {
        return state;
    }
}
