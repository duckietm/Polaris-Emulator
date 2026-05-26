package com.eu.habbo.habbohotel.games;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredHighscore;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredBlob;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreDataEntry;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.guides.GuideSessionPartnerIsPlayingComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.games.GameHabboJoinEvent;
import com.eu.habbo.plugin.events.games.GameHabboLeaveEvent;
import com.eu.habbo.plugin.events.games.GameStartedEvent;
import com.eu.habbo.plugin.events.games.GameStoppedEvent;
import com.eu.habbo.threading.runnables.SaveScoreForTeam;
import gnu.trove.map.hash.THashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

public abstract class Game implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Game.class);
    protected final THashMap<GameTeamColors, GameTeam> teams = new THashMap<>();
    protected final Room room;
    private final Class<? extends GameTeam> gameTeamClazz;
    private final Class<? extends GamePlayer> gamePlayerClazz;
    private final boolean countsAchievements;
    public boolean isRunning;
    public GameState state = GameState.IDLE;
    private int startTime;
    private int endTime;

    public Game(Class<? extends GameTeam> gameTeamClazz, Class<? extends GamePlayer> gamePlayerClazz, Room room, boolean countsAchievements) {
        this.gameTeamClazz = gameTeamClazz;
        this.gamePlayerClazz = gamePlayerClazz;
        this.room = room;
        this.countsAchievements = countsAchievements;
    }


    public abstract void initialise();


    public boolean addHabbo(Habbo habbo, GameTeamColors teamColor) {
        try {
            if (habbo != null) {
                if (Emulator.getPluginManager().isRegistered(GameHabboJoinEvent.class, true)) {
                    Event gameHabboJoinEvent = new GameHabboJoinEvent(this, habbo);
                    Emulator.getPluginManager().fireEvent(gameHabboJoinEvent);
                    if (gameHabboJoinEvent.isCancelled())
                        return false;
                }

                synchronized (this.teams) {
                    GameTeam team = this.getTeam(teamColor);
                    if (team == null) {
                        team = this.gameTeamClazz.getDeclaredConstructor(GameTeamColors.class).newInstance(teamColor);
                        this.addTeam(team);
                    }

                    GamePlayer player = this.gamePlayerClazz.getDeclaredConstructor(Habbo.class, GameTeamColors.class).newInstance(habbo, teamColor);
                    team.addMember(player);
                    habbo.getHabboInfo().setCurrentGame(this.getClass());
                    habbo.getHabboInfo().setGamePlayer(player);
                }
                habbo.getClient().sendResponse(new GuideSessionPartnerIsPlayingComposer(true));
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return false;
    }


    public void removeHabbo(Habbo habbo) {
        if (habbo != null) {
            if (Emulator.getPluginManager().isRegistered(GameHabboLeaveEvent.class, true)) {
                Event gameHabboLeaveEvent = new GameHabboLeaveEvent(this, habbo);
                Emulator.getPluginManager().fireEvent(gameHabboLeaveEvent);
                if (gameHabboLeaveEvent.isCancelled())
                    return;
            }

            GameTeam team = this.getTeamForHabbo(habbo);
            if (team != null && team.isMember(habbo)) {
                if (habbo.getHabboInfo().getGamePlayer() != null) {
                    team.removeMember(habbo.getHabboInfo().getGamePlayer());
                    if (habbo.getHabboInfo().getGamePlayer() != null) {
                        habbo.getHabboInfo().getGamePlayer().reset();
                    }
                }

                habbo.getHabboInfo().setCurrentGame(null);
                habbo.getHabboInfo().setGamePlayer(null);
                habbo.getClient().sendResponse(new GuideSessionPartnerIsPlayingComposer(false));
                if (this.countsAchievements && this.endTime > this.startTime) {
                    AchievementManager.progressAchievement(habbo, Emulator.getGameEnvironment().getAchievementManager().getAchievement("GamePlayed"));
                }
            }
        }
    }


    public void start() {
        this.isRunning = false;
        this.state = GameState.RUNNING;
        this.startTime = Emulator.getIntUnixTimestamp();

        if (Emulator.getPluginManager().isRegistered(GameStartedEvent.class, true)) {
            Event gameStartedEvent = new GameStartedEvent(this);
            Emulator.getPluginManager().fireEvent(gameStartedEvent);
        }

        for (HabboItem item : this.room.getRoomSpecialTypes().getItemsOfType(WiredBlob.class)) {
            ((WiredBlob) item).onGameStart(this.room);
        }

        for (GameTeam team : this.teams.values()) {
            team.resetScores();
        }
    }

    public void onEnd() {
        this.endTime = Emulator.getIntUnixTimestamp();

        this.saveScores();

        int totalPointsGained = this.teams.values().stream().mapToInt(GameTeam::getTotalScore).sum();

        Habbo roomOwner = Emulator.getGameEnvironment().getHabboManager().getHabbo(this.room.getOwnerId());
        if (roomOwner != null) {
            AchievementManager.progressAchievement(roomOwner, Emulator.getGameEnvironment().getAchievementManager().getAchievement("GameAuthorExperience"), totalPointsGained);
        }

        GameTeam winningTeam = null;
        if(totalPointsGained > 0) {
            for (GameTeam team : this.teams.values()) {
                if (winningTeam == null || team.getTotalScore() > winningTeam.getTotalScore()) {
                    winningTeam = team;
                }
            }
        }

        if (winningTeam != null) {
            for (GamePlayer player : winningTeam.getMembers()) {
                WiredManager.triggerTeamWins(this.room, player.getHabbo().getRoomUnit());

                Habbo winner = player.getHabbo();
                if (winner != null) {
                    AchievementManager.progressAchievement(roomOwner, Emulator.getGameEnvironment().getAchievementManager().getAchievement("GamePlayerExperience"));
                }
            }

            if (winningTeam.getMembers().size() > 0) {
                for (HabboItem item : this.room.getRoomSpecialTypes().getItemsOfType(InteractionWiredHighscore.class)) {
                    Emulator.getGameEnvironment().getItemManager().getHighscoreManager().addHighscoreData(new WiredHighscoreDataEntry(item.getId(), winningTeam.getMembers().stream().map(m -> m.getHabbo().getHabboInfo().getId()).collect(Collectors.toList()), winningTeam.getTotalScore(), true, Emulator.getIntUnixTimestamp()));
                }
            }

            for (GameTeam team : this.teams.values()) {
                if (team == winningTeam) continue;

                for (GamePlayer player : team.getMembers()) {
                    WiredManager.triggerTeamLoses(this.room, player.getHabbo().getRoomUnit());
                }

                if (team.getMembers().size() > 0 && team.getTotalScore() > 0) {
                    for (HabboItem item : this.room.getRoomSpecialTypes().getItemsOfType(InteractionWiredHighscore.class)) {
                        Emulator.getGameEnvironment().getItemManager().getHighscoreManager().addHighscoreData(new WiredHighscoreDataEntry(item.getId(), team.getMembers().stream().map(m -> m.getHabbo().getHabboInfo().getId()).collect(Collectors.toList()), team.getTotalScore(), false, Emulator.getIntUnixTimestamp()));
                    }
                }
            }
        }

        for (HabboItem item : this.room.getRoomSpecialTypes().getItemsOfType(InteractionWiredHighscore.class)) {
            ((InteractionWiredHighscore) item).reloadData();
            this.room.updateItem(item);
        }

        for (HabboItem item : this.room.getRoomSpecialTypes().getItemsOfType(WiredBlob.class)) {
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

    public void stop() {
        this.state = GameState.IDLE;

        boolean gamesActive = false;
        for (InteractionGameTimer timer : room.getRoomSpecialTypes().getGameTimers().values()) {
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
        if (this.room == null)
            return;

        THashMap<GameTeamColors, GameTeam> teamsCopy = new THashMap<>();
        teamsCopy.putAll(this.teams);

        for (Map.Entry<GameTeamColors, GameTeam> teamEntry : teamsCopy.entrySet()) {
            Emulator.getThreading().run(new SaveScoreForTeam(teamEntry.getValue(), this));
        }
    }


    public GameTeam getTeamForHabbo(Habbo habbo) {
        if (habbo != null) {
            synchronized (this.teams) {
                for (GameTeam team : this.teams.values()) {
                    if (team.isMember(habbo)) {
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

    public THashMap<GameTeamColors, GameTeam> getTeams() {
        return teams;
    }

    public boolean isCountsAchievements() {
        return countsAchievements;
    }

    public GameState getState() {
        return state;
    }
}
