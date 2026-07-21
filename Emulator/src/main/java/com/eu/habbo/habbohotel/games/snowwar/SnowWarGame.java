package com.eu.habbo.habbohotel.games.snowwar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarCreateSnowballEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarLaunchSnowballEvent;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMap;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMapsManager;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarAvatarObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarGameObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarMachineObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarSnowballObject;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormFullGameStatusComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGameEndedComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormIntializeGameArenaViewComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormLevelDataComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormOnGameEndingComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormOnPlayerExitedArenaComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormOnStageEndingComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormOnStageRunningComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormOnStageStartComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormRejoinPreviousRoomComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormUserChatMessageComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormUserRematchedComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One SnowWar match. Lifecycle per PROTOCOL.md "Game flow":
 * arena init -> (all clients LoadStageReady) -> LevelData + FullGameStatus +
 * OnStageStart countdown -> RUNNING with a 300ms tick task -> ENDING/ENDED.
 */
public class SnowWarGame {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowWarGame.class);

    private static final long LOAD_STAGE_TIMEOUT_MS = 10000;

    private final int id;
    private final int mapId;
    private final SnowWarMap map;
    private final int teamCount = 2;

    private final Map<Integer, SnowWarGamePlayer> players = new LinkedHashMap<>();
    private final List<SnowWarMachineObject> machines = new ArrayList<>();
    private final AtomicInteger objectIdCounter = new AtomicInteger(0);
    private final Random random = new Random();

    private volatile SnowWarGameState state = SnowWarGameState.WAITING_FOR_PLAYERS;
    private volatile SnowWarGameTask task = null;

    private final int gameLengthSeconds;
    private final int preparingSeconds;
    private final int restartSeconds;
    private volatile long endTimeMillis = 0;
    private volatile long restartWindowEndMillis = 0;

    public SnowWarGame(int id, int mapId, List<Habbo> participants) {
        this.id = id;
        this.mapId = mapId;
        this.map = SnowWarMapsManager.getMap(mapId);

        this.gameLengthSeconds = Emulator.getConfig().getInt("gamecenter.snowwar.game.length.seconds", 180);
        this.preparingSeconds = Emulator.getConfig().getInt("gamecenter.snowwar.preparing.seconds", 10);
        this.restartSeconds = Emulator.getConfig().getInt("gamecenter.snowwar.restart.seconds", 30);

        // Team assignment round-robin at match creation.
        int index = 0;
        for (Habbo habbo : participants) {
            SnowWarGamePlayer player = new SnowWarGamePlayer(habbo, this.nextObjectId(), index % this.teamCount);
            synchronized (this.players) {
                this.players.put(player.getUserId(), player);
            }
            index++;
        }

        if (this.map != null) {
            for (SnowWarPoint machinePosition : this.map.getMachinePositions()) {
                this.machines.add(new SnowWarMachineObject(this.nextObjectId(),
                    machinePosition.getX(), machinePosition.getY()));
            }
        }
    }

    public int getId() {
        return this.id;
    }

    public int getMapId() {
        return this.mapId;
    }

    public SnowWarMap getMap() {
        return this.map;
    }

    public int getTeamCount() {
        return this.teamCount;
    }

    public int getGameLengthSeconds() {
        return this.gameLengthSeconds;
    }

    public int getRestartSeconds() {
        return this.restartSeconds;
    }

    public SnowWarGameState getState() {
        return this.state;
    }

    public List<SnowWarMachineObject> getMachines() {
        return this.machines;
    }

    public int nextObjectId() {
        return this.objectIdCounter.incrementAndGet();
    }

    public List<SnowWarGamePlayer> getActivePlayers() {
        synchronized (this.players) {
            return new ArrayList<>(this.players.values());
        }
    }

    public SnowWarGamePlayer getPlayer(int userId) {
        synchronized (this.players) {
            return this.players.get(userId);
        }
    }

    public SnowWarGamePlayer getPlayerByObjectId(int objectId) {
        for (SnowWarGamePlayer player : this.getActivePlayers()) {
            if (player.getObjectId() == objectId) {
                return player;
            }
        }
        return null;
    }

    public int getTotalSecondsLeft() {
        if (this.state != SnowWarGameState.RUNNING) {
            return 0;
        }
        long left = (this.endTimeMillis - System.currentTimeMillis()) / 1000;
        return left > 0 ? (int) left : 0;
    }

    public boolean isOpponent(SnowWarGamePlayer p1, SnowWarGamePlayer p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        if (p1.getUserId() == p2.getUserId()) {
            return false;
        }
        if (this.teamCount == 1) {
            return true;
        }
        return p1.getTeamId() != p2.getTeamId();
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    public void start() {
        if (this.map == null) {
            LOGGER.error("SnowWar game {} could not start: map {} failed to load.", this.id, this.mapId);
            this.state = SnowWarGameState.ENDED;
            SnowWarManager.getInstance().onGameFinished(this);
            return;
        }

        this.broadcast(new SnowStormIntializeGameArenaViewComposer());

        Emulator.getThreading().run(() -> {
            if (this.state == SnowWarGameState.WAITING_FOR_PLAYERS) {
                LOGGER.info("SnowWar game {}: load stage timeout reached, starting anyway.", this.id);
                this.beginPreparing();
            }
        }, LOAD_STAGE_TIMEOUT_MS);
    }

    public void onLoadStageReady(int userId) {
        SnowWarGamePlayer player = this.getPlayer(userId);
        if (player == null || this.state != SnowWarGameState.WAITING_FOR_PLAYERS) {
            return;
        }

        player.setStageReady(true);

        for (SnowWarGamePlayer p : this.getActivePlayers()) {
            if (!p.isStageReady()) {
                return;
            }
        }

        this.beginPreparing();
    }

    private synchronized void beginPreparing() {
        if (this.state != SnowWarGameState.WAITING_FOR_PLAYERS) {
            return;
        }

        this.state = SnowWarGameState.PREPARING;
        this.assignSpawnPoints();

        this.broadcast(new SnowStormLevelDataComposer(this));
        this.broadcast(this.createFullGameStatusComposer());
        this.broadcast(new SnowStormOnStageStartComposer(this.preparingSeconds));

        Emulator.getThreading().run(this::beginRunning, this.preparingSeconds * 1000L);
    }

    private void assignSpawnPoints() {
        List<SnowWarPoint> occupied = new ArrayList<>();
        int[] teamMemberIndex = new int[this.teamCount];

        int centerX = this.map.getSizeX() / 2;
        int centerY = this.map.getSizeY() / 2;

        for (SnowWarGamePlayer player : this.getActivePlayers()) {
            // Distribute teams over the spawn clusters: team 0 -> clusters 0, 2, ...
            int clusterIndex = player.getTeamId() + (teamMemberIndex[player.getTeamId()] % 2) * this.teamCount;
            teamMemberIndex[player.getTeamId()]++;

            SnowWarPoint spawn = this.map.generateSpawn(clusterIndex, occupied, this.random);
            occupied.add(spawn);

            SnowWarAttributes attr = player.getAttributes();
            attr.resetForSpawn(spawn);

            // Face the middle of the arena.
            int direction = SnowWarMath.getAngleFromComponents(
                SnowWarMath.tileToWorld(centerX) - attr.getWorldPosition().getX(),
                SnowWarMath.tileToWorld(centerY) - attr.getWorldPosition().getY());
            attr.setRotation(SnowWarMath.direction360To8(direction));

            player.setAvatar(new SnowWarAvatarObject(this, player));
        }
    }

    private synchronized void beginRunning() {
        if (this.state != SnowWarGameState.PREPARING) {
            return;
        }

        this.state = SnowWarGameState.RUNNING;
        this.endTimeMillis = System.currentTimeMillis() + (this.gameLengthSeconds * 1000L);

        this.broadcast(new SnowStormOnStageRunningComposer(this.gameLengthSeconds));

        this.task = new SnowWarGameTask(this);
        Emulator.getThreading().run(this.task, SnowWarConstants.SERVER_TICK_MS);
    }

    public SnowWarGameTask getTask() {
        return this.task;
    }

    public synchronized void endGame() {
        if (this.state == SnowWarGameState.ENDING || this.state == SnowWarGameState.ENDED) {
            return;
        }

        this.state = SnowWarGameState.ENDING;

        this.broadcast(new SnowStormOnStageEndingComposer());
        this.broadcast(new SnowStormOnGameEndingComposer(this.restartSeconds, this));
        this.broadcast(new SnowStormGameEndedComposer());

        this.state = SnowWarGameState.ENDED;
        this.restartWindowEndMillis = System.currentTimeMillis() + (this.restartSeconds * 1000L);

        Emulator.getThreading().run(this::cleanup, this.restartSeconds * 1000L);
    }

    private void cleanup() {
        for (SnowWarGamePlayer player : this.getActivePlayers()) {
            // Players who rematched may already be part of a new game -
            // never wipe attributes that belong to another running match.
            SnowWarGame currentGame = SnowWarManager.getInstance().getGameByUserId(player.getUserId());
            if (currentGame == null || currentGame == this) {
                SnowWarPlayers.remove(player.getUserId());
            }
            SnowWarManager.getInstance().clearUserGameIfMatches(player.getUserId(), this);
        }
        synchronized (this.players) {
            this.players.clear();
        }
        SnowWarManager.getInstance().onGameFinished(this);
    }

    public void exitGame(int userId) {
        SnowWarGamePlayer player;
        synchronized (this.players) {
            player = this.players.remove(userId);
        }

        if (player == null) {
            return;
        }

        SnowWarPlayers.remove(userId);
        SnowWarManager.getInstance().clearUserGame(userId);

        this.broadcast(new SnowStormOnPlayerExitedArenaComposer(player.getObjectId(), userId));

        if (player.getHabbo().getClient() != null) {
            player.getHabbo().getClient().sendResponse(new SnowStormRejoinPreviousRoomComposer());
        }

        int remainingPlayers;
        synchronized (this.players) {
            remainingPlayers = this.players.size();
        }

        // A match normally needs 2+ players to continue, but when the hotel
        // configures gamecenter.snowwar.players.min=1 (solo testing) a lone
        // player may keep playing until they leave.
        int endThreshold = Math.min(2,
            Math.max(1, Emulator.getConfig().getInt("gamecenter.snowwar.players.min", 2)));

        if (remainingPlayers < endThreshold && (this.state == SnowWarGameState.WAITING_FOR_PLAYERS
            || this.state == SnowWarGameState.PREPARING
            || this.state == SnowWarGameState.RUNNING)) {
            this.endGame();
            return;
        }

        if (this.state == SnowWarGameState.WAITING_FOR_PLAYERS) {
            boolean allReady = true;
            List<SnowWarGamePlayer> remaining = this.getActivePlayers();
            for (SnowWarGamePlayer p : remaining) {
                if (!p.isStageReady()) {
                    allReady = false;
                    break;
                }
            }
            if (allReady && !remaining.isEmpty()) {
                this.beginPreparing();
            }
        }

        if (remainingPlayers == 0 && this.state == SnowWarGameState.ENDED) {
            SnowWarManager.getInstance().onGameFinished(this);
        }
    }

    // ========================================================================
    // Player actions (called from packet handlers)
    // ========================================================================

    public void handleWalk(SnowWarGamePlayer player, int worldX, int worldY) {
        if (this.state != SnowWarGameState.RUNNING) {
            return;
        }

        SnowWarAttributes attr = player.getAttributes();

        if (!attr.isWalkableState() && attr.getActivityState() != SnowWarActivityState.CREATING_SNOWBALL) {
            return;
        }

        int tileX = SnowWarMath.convertToGameCoordinate(worldX);
        int tileY = SnowWarMath.convertToGameCoordinate(worldY);

        if (this.map.getTile(tileX, tileY) == null) {
            return;
        }

        attr.setGoalWorldCoordinates(new SnowWarPoint(worldX, worldY));
        attr.setWalkGoal(new SnowWarPoint(tileX, tileY));
        attr.setPathfindIterations(0);
        attr.setWalking(true);
    }

    public void handleThrowAtLocation(SnowWarGamePlayer player, int worldX, int worldY, int trajectory) {
        if (trajectory != 1 && trajectory != 2) {
            return;
        }

        this.throwSnowball(player, worldX, worldY, trajectory);
    }

    public void handleThrowAtPlayer(SnowWarGamePlayer player, int targetObjectId, int trajectory) {
        if (trajectory != 0 && trajectory != 1 && trajectory != 2) {
            return;
        }

        SnowWarGamePlayer target = this.getPlayerByObjectId(targetObjectId);
        if (target == null || !this.isOpponent(player, target)) {
            return;
        }

        SnowWarPoint targetWorld = target.getAttributes().getWorldPosition();
        this.throwSnowball(player, targetWorld.getX(), targetWorld.getY(), trajectory);
    }

    /**
     * Common throw path (README 13.4).
     */
    private void throwSnowball(SnowWarGamePlayer player, int worldX, int worldY, int trajectory) {
        if (this.state != SnowWarGameState.RUNNING || this.task == null) {
            return;
        }

        SnowWarAttributes attr = player.getAttributes();

        if (!attr.isWalkableState()) {
            return;
        }
        if (attr.getLastThrowTime() + SnowWarConstants.THROW_COOLDOWN_MS > System.currentTimeMillis()) {
            return;
        }
        if (attr.getSnowballCount().get() <= 0) {
            return;
        }

        if (attr.isWalking() && player.getAvatar() != null) {
            player.getAvatar().stopWalking();
        }

        int objectId = this.nextObjectId();
        int targetTileX = SnowWarMath.convertToGameCoordinate(worldX);
        int targetTileY = SnowWarMath.convertToGameCoordinate(worldY);

        SnowWarSnowballObject snowball = new SnowWarSnowballObject(
            objectId,
            this.map,
            player,
            attr.getCurrentPosition().getX(),
            attr.getCurrentPosition().getY(),
            targetTileX,
            targetTileY,
            trajectory);

        this.task.addSnowball(snowball);
        attr.getSnowballCount().decrementAndGet();
        attr.setLastThrowTime(System.currentTimeMillis());

        // Update facing direction.
        int currentWorldX = SnowWarMath.tileToWorld(attr.getCurrentPosition().getX());
        int currentWorldY = SnowWarMath.tileToWorld(attr.getCurrentPosition().getY());
        int direction = SnowWarMath.getAngleFromComponents(worldX - currentWorldX, worldY - currentWorldY);
        attr.setRotation(SnowWarMath.direction360To8(direction));

        int convertedTargetX = SnowWarMath.convertToWorldCoordinate(targetTileX);
        int convertedTargetY = SnowWarMath.convertToWorldCoordinate(targetTileY);

        this.task.queueEvent(new SnowWarLaunchSnowballEvent(
            objectId, player.getObjectId(), convertedTargetX, convertedTargetY, trajectory));
    }

    public void handleCreateSnowball(SnowWarGamePlayer player) {
        if (this.state != SnowWarGameState.RUNNING || this.task == null) {
            return;
        }

        SnowWarAttributes attr = player.getAttributes();

        if (attr.getActivityState() != SnowWarActivityState.NORMAL) {
            return;
        }
        if (attr.getSnowballCount().get() >= SnowWarConstants.MAX_SNOWBALLS) {
            return;
        }

        attr.setActivityState(SnowWarActivityState.CREATING_SNOWBALL);
        attr.setActivityTimer(SnowWarConstants.CREATING_TIMER);

        this.task.queueEvent(new SnowWarCreateSnowballEvent(player.getObjectId()));
    }

    public void handleChat(SnowWarGamePlayer player, String message) {
        if (message == null) {
            return;
        }

        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (trimmed.length() > 100) {
            trimmed = trimmed.substring(0, 100);
        }

        trimmed = Emulator.getGameEnvironment().getWordFilter().filter(trimmed, player.getHabbo());
        if (trimmed == null || trimmed.trim().isEmpty()) {
            return;
        }

        this.broadcast(new SnowStormUserChatMessageComposer(player.getObjectId(), trimmed));
    }

    public void handlePlayAgain(SnowWarGamePlayer player) {
        if (this.state != SnowWarGameState.ENDED) {
            return;
        }
        if (System.currentTimeMillis() > this.restartWindowEndMillis) {
            return;
        }
        if (player.isPlayAgain()) {
            return;
        }

        player.setPlayAgain(true);
        this.broadcast(new SnowStormUserRematchedComposer(player.getUserId()));

        SnowWarManager.getInstance().clearUserGame(player.getUserId());
        SnowWarManager.getInstance().joinQueue(player.getHabbo());
    }

    public void sendFullGameStatus(SnowWarGamePlayer player) {
        if (player.getHabbo().getClient() != null) {
            player.getHabbo().getClient().sendResponse(this.createFullGameStatusComposer());
        }
    }

    public SnowStormFullGameStatusComposer createFullGameStatusComposer() {
        List<SnowWarGameObject> objects = new ArrayList<>();

        objects.addAll(this.machines);

        for (SnowWarGamePlayer player : this.getActivePlayers()) {
            if (player.getAvatar() != null) {
                objects.add(player.getAvatar());
            }
        }

        int turn = 0;
        int checksum;
        List<SnowWarSnowballObject> snowballs;

        if (this.task != null) {
            turn = this.task.getCurrentTurn();
            snowballs = this.task.getSnowballsSnapshot();
            objects.addAll(snowballs);
            checksum = this.task.getCurrentChecksum();
        } else {
            snowballs = new ArrayList<>();
            checksum = SnowWarChecksumCalculator.calculate(this, snowballs, turn);
        }

        return new SnowStormFullGameStatusComposer(turn, checksum, this.getTotalSecondsLeft(), objects);
    }

    // ========================================================================
    // Messaging
    // ========================================================================

    public void broadcast(MessageComposer composer) {
        ServerMessage message = composer.compose();

        for (SnowWarGamePlayer player : this.getActivePlayers()) {
            if (player.getHabbo().getClient() != null) {
                player.getHabbo().getClient().sendResponse(message);
            }
        }
    }
}
