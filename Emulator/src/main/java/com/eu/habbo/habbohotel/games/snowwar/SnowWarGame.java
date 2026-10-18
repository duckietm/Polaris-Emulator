package com.eu.habbo.habbohotel.games.snowwar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarCreateSnowballEvent;
import com.eu.habbo.habbohotel.games.snowwar.events.SnowWarLaunchSnowballEvent;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarItemProperties;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMap;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMapsManager;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarTile;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarAvatarObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarGameObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarMachineObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarPileObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarSnowballObject;
import com.eu.habbo.habbohotel.games.snowwar.objects.SnowWarTreeObject;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.gamecenter.Game2UserBlockedComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormFullGameStatusComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGameEndedComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGenericErrorComposer;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final List<SnowWarPileObject> piles = new ArrayList<>();
    private final List<SnowWarTreeObject> trees = new ArrayList<>();
    private final AtomicInteger objectIdCounter = new AtomicInteger(0);
    private final Random random = new Random();

    private volatile SnowWarGameState state = SnowWarGameState.WAITING_FOR_PLAYERS;
    private volatile SnowWarGameTask task = null;

    private final int gameLengthSeconds;
    private final int preparingSeconds;
    private final int restartSeconds;
    private volatile long endTimeMillis = 0;
    private volatile long restartWindowEndMillis = 0;
    private final long idleTimeoutMs;
    private final long idleWarningMs;

    /** All-time saved scores read when the match starts; {@code null} until loaded or if loading failed. */
    private volatile Map<Integer, Integer> savedTotalsAtStart = null;

    private static final int PREFETCH_ATTEMPTS = 3;
    private static final long PREFETCH_RETRY_MS = 15_000L;

    public SnowWarGame(int id, int mapId, List<Habbo> participants) {
        this.id = id;
        this.mapId = mapId;
        this.map = SnowWarMapsManager.getMap(mapId);

        this.gameLengthSeconds = setting("gamecenter.snowwar.game.length.seconds", 180);
        this.preparingSeconds = setting("gamecenter.snowwar.preparing.seconds", 10);
        this.restartSeconds = setting("gamecenter.snowwar.restart.seconds", 30);
        // 0 switches idle removal off; the warning comes this long before removal.
        int idleTimeoutSeconds = setting("gamecenter.snowwar.idle.timeout.seconds", 60);
        int idleWarningSeconds = setting("gamecenter.snowwar.idle.warning.seconds", 15);
        this.idleTimeoutMs = idleTimeoutSeconds <= 0 ? 0 : Math.max(20, idleTimeoutSeconds) * 1000L;
        this.idleWarningMs = Math.min(this.idleTimeoutMs, Math.max(5, idleWarningSeconds) * 1000L);

        // Teams at match creation: rematchers keep their team, the rest fill
        // the smaller team, then the sizes are balanced (SnowWarTeams). The
        // lobby line-up uses the same split.
        List<Integer> participantIds = new ArrayList<>();
        for (Habbo habbo : participants) {
            participantIds.add(habbo.getHabboInfo().getId());
        }
        Map<Integer, Integer> teams =
                SnowWarTeams.assign(participantIds, SnowWarManager.getInstance().getPreferredTeams(), this.teamCount);
        for (Habbo habbo : participants) {
            int userId = habbo.getHabboInfo().getId();
            SnowWarGamePlayer player = new SnowWarGamePlayer(habbo, this.nextObjectId(), teams.getOrDefault(userId, 0));
            synchronized (this.players) {
                this.players.put(player.getUserId(), player);
            }
        }

        if (this.map != null) {
            this.map.getItems().stream()
                    .filter(item -> SnowWarItemProperties.isTreeName(item.getName()))
                    .map(item -> new SnowWarTreeObject(this.nextObjectId(), item))
                    .forEach(this.trees::add);
            this.map.getItems().stream()
                    .filter(item -> item.getName().equals("snst_ballpile"))
                    .map(item -> new SnowWarPileObject(this.nextObjectId(), item.getX(), item.getY()))
                    .forEach(this.piles::add);
            for (SnowWarPoint machinePosition : this.map.getMachinePositions()) {
                this.machines.add(
                        new SnowWarMachineObject(this.nextObjectId(), machinePosition.getX(), machinePosition.getY()));
            }
        }
    }

    /** All SnowWar match settings come from emulator_settings through here. */
    private static int setting(String key, int defaultValue) {
        return Emulator.getConfig().getInt(key, defaultValue);
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

    public List<SnowWarTreeObject> getTrees() {
        return this.trees;
    }

    public List<SnowWarPileObject> getPiles() {
        return this.piles;
    }

    public int nextObjectId() {
        return this.objectIdCounter.incrementAndGet();
    }

    public int reserveObjectIds(int count) {
        return this.objectIdCounter.getAndAdd(count) + 1;
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

        Emulator.getThreading()
                .run(
                        () -> {
                            if (this.state == SnowWarGameState.WAITING_FOR_PLAYERS) {
                                LOGGER.info("SnowWar game {}: load stage timeout reached, starting anyway.", this.id);
                                this.beginPreparing();
                            }
                        },
                        LOAD_STAGE_TIMEOUT_MS);
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
        this.prefetchSavedTotals();

        // LevelData carries a per-recipient editor flag, so it is composed
        // per player instead of broadcast.
        for (SnowWarGamePlayer player : this.getActivePlayers()) {
            if (player.getHabbo() == null || player.getHabbo().getClient() == null) {
                continue;
            }
            boolean canEdit = player.getHabbo().hasPermission(SnowWarManager.BUILD_PERMISSION);
            player.getHabbo().getClient().sendResponse(new SnowStormLevelDataComposer(this, canEdit));
        }
        this.broadcast(this.createFullGameStatusComposer());
        this.broadcast(new SnowStormOnStageStartComposer(this.preparingSeconds));

        Emulator.getThreading().run(this::beginRunning, this.preparingSeconds * 1000L);
    }

    /**
     * Reads the players' saved totals during the "get ready" countdown, so the
     * results screen can show skill levels without a database call at the end.
     */
    private void prefetchSavedTotals() {
        List<Integer> userIds = new ArrayList<>();
        for (SnowWarGamePlayer player : this.getActivePlayers()) {
            userIds.add(player.getUserId());
        }
        this.prefetchSavedTotals(userIds, 0);
    }

    /**
     * Loads the all-time totals in the background; a failed read is retried
     * while the match runs, so endGame (on the game tick) never has to touch
     * the database itself.
     */
    private void prefetchSavedTotals(List<Integer> userIds, int attempt) {
        Emulator.getThreading()
                .run(
                        () -> {
                            Map<Integer, Integer> totals =
                                    SnowWarManager.getInstance().loadSavedTotalScores(userIds);
                            if (totals != null) {
                                this.savedTotalsAtStart = totals;
                            } else if (attempt + 1 < PREFETCH_ATTEMPTS
                                    && this.state != SnowWarGameState.ENDING
                                    && this.state != SnowWarGameState.ENDED) {
                                this.prefetchSavedTotals(userIds, attempt + 1);
                            }
                        },
                        attempt == 0 ? 0 : PREFETCH_RETRY_MS);
    }

    private void assignSpawnPoints() {
        List<SnowWarPoint> occupied = new ArrayList<>();

        int centerX = this.map.getSizeX() / 2;
        int centerY = this.map.getSizeY() / 2;

        for (SnowWarGamePlayer player : this.getActivePlayers()) {
            // Random walkable tile, spread away from already-placed players.
            SnowWarPoint spawn = this.map.generateSpawn(occupied, this.random);
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

        // Publish the task before the state, so an action that sees RUNNING
        // always finds the task it has to lock.
        this.task = new SnowWarGameTask(this);
        long now = System.currentTimeMillis();
        for (SnowWarGamePlayer player : this.getActivePlayers()) {
            player.markActive(now);
        }
        this.endTimeMillis = now + (this.gameLengthSeconds * 1000L);
        this.state = SnowWarGameState.RUNNING;

        this.broadcast(new SnowStormOnStageRunningComposer(this.gameLengthSeconds));

        this.task.start();
    }

    public SnowWarGameTask getTask() {
        return this.task;
    }

    public synchronized void endGame() {
        if (this.state == SnowWarGameState.ENDING || this.state == SnowWarGameState.ENDED) {
            return;
        }

        this.state = SnowWarGameState.ENDING;

        List<SnowWarGamePlayer> finalPlayers = this.getActivePlayers();
        SnowWarManager.getInstance().recordScores(finalPlayers);
        List<Integer> finalPlayerIds = new ArrayList<>();
        for (SnowWarGamePlayer player : finalPlayers) {
            finalPlayerIds.add(player.getUserId());
        }
        // Scores were queued just above, so they count as pending on top of
        // the totals read at the start. endGame runs on the game tick, so it
        // never reads the database: if every background read failed, the
        // levels come from the pending scores alone.
        Map<Integer, Integer> savedTotals = this.savedTotalsAtStart;
        Map<Integer, Integer> skillLevels = SnowWarManager.getInstance()
                .skillLevelsFromSavedTotals(finalPlayerIds, savedTotals != null ? savedTotals : Map.of());

        this.broadcast(new SnowStormOnStageEndingComposer());
        this.broadcast(new SnowStormOnGameEndingComposer(this.restartSeconds, this, skillLevels));
        this.broadcast(new SnowStormGameEndedComposer());

        this.state = SnowWarGameState.ENDED;
        this.restartWindowEndMillis = System.currentTimeMillis() + (this.restartSeconds * 1000L);

        Emulator.getThreading().run(this::cleanup, this.restartSeconds * 1000L);
    }

    private void cleanup() {
        // Rematchers still waiting on this match (online, not moved on).
        List<Habbo> rematchers = new ArrayList<>();
        Map<Integer, Integer> rematchTeams = new HashMap<>();
        for (SnowWarGamePlayer player : this.getActivePlayers()) {
            if (player.isPlayAgain()
                    && player.getHabbo().getClient() != null
                    && SnowWarManager.getInstance().getGameByUserId(player.getUserId()) == this) {
                rematchers.add(player.getHabbo());
                rematchTeams.put(player.getUserId(), player.getTeamId());
            }
        }

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

        if (!rematchers.isEmpty()) {
            SnowWarManager.getInstance().enqueueRematch(rematchers, rematchTeams);
        }
    }

    public void exitGame(int userId) {
        SnowWarGamePlayer player = this.removeLeaver(userId);

        if (player == null) {
            return;
        }

        // AIR Game2UserBlocked (3508): abandoning a live match blocks starting
        // a new one for a while (GamesMainViewController.changeBlockStatus).
        // Kept on the server too, so it also holds after a disconnect.
        int blockSeconds = this.state == SnowWarGameState.RUNNING
                ? Math.max(0, setting("gamecenter.game.leave.block.seconds", 180))
                : 0;
        SnowWarManager.getInstance().blockUser(userId, blockSeconds);

        if (player.getHabbo().getClient() != null) {
            player.getHabbo().getClient().sendResponse(new SnowStormRejoinPreviousRoomComposer());

            if (blockSeconds > 0) {
                player.getHabbo().getClient().sendResponse(new Game2UserBlockedComposer(blockSeconds));
            }
        }

        int remainingPlayers;
        synchronized (this.players) {
            remainingPlayers = this.players.size();
        }

        // A match normally needs 2+ players to continue, but when the hotel
        // configures gamecenter.snowwar.players.min=1 (solo testing) a lone
        // player may keep playing until they leave.
        int endThreshold = Math.min(2, SnowWarManager.getInstance().getMinimumPlayers());

        if (remainingPlayers < endThreshold
                && (this.state == SnowWarGameState.WAITING_FOR_PLAYERS
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

    /**
     * Removes a leaver under the tick monitor while a match runs (the lock
     * {@link #runAction} uses), so a tick in progress finishes and sends the
     * leaver's last events before PlayerExitedArena goes out. Only the
     * removal is locked; ending the game happens outside it.
     */
    private SnowWarGamePlayer removeLeaver(int userId) {
        SnowWarGameTask current = this.task;
        if (current == null) {
            return this.removeAndAnnounce(userId);
        }
        synchronized (current) {
            return this.removeAndAnnounce(userId);
        }
    }

    private SnowWarGamePlayer removeAndAnnounce(int userId) {
        SnowWarGamePlayer player;
        synchronized (this.players) {
            player = this.players.remove(userId);
        }

        if (player == null) {
            return null;
        }

        SnowWarPlayers.remove(userId);
        SnowWarManager.getInstance().clearUserGame(userId);

        this.broadcast(new SnowStormOnPlayerExitedArenaComposer(player.getObjectId(), userId));
        return player;
    }

    // ========================================================================
    // Idle players (called from the game loop)
    // ========================================================================

    enum IdleStatus {
        ACTIVE,
        WARN,
        REMOVE
    }

    /** What to do with a player who has been idle for {@code idleMs}; a timeout of 0 means never. */
    static IdleStatus idleStatus(long idleMs, boolean warned, long timeoutMs, long warningMs) {
        if (timeoutMs <= 0) {
            return IdleStatus.ACTIVE;
        }
        if (idleMs >= timeoutMs) {
            return IdleStatus.REMOVE;
        }
        if (!warned && idleMs >= timeoutMs - warningMs) {
            return IdleStatus.WARN;
        }
        return IdleStatus.ACTIVE;
    }

    /**
     * Warns players who have done nothing for a while and removes them when
     * they still do nothing, so an AFK player cannot spoil a match.
     */
    void checkIdlePlayers(long now) {
        for (SnowWarGamePlayer player : this.getActivePlayers()) {
            IdleStatus status = idleStatus(
                    now - player.getLastActionTime(), player.isIdleWarned(), this.idleTimeoutMs, this.idleWarningMs);
            if (status == IdleStatus.WARN) {
                player.setIdleWarned(true);
                this.sendTo(player, new SnowStormGenericErrorComposer(SnowWarConstants.ERROR_IDLE_WARNING));
            } else if (status == IdleStatus.REMOVE) {
                LOGGER.info("SnowWar game {}: removing idle player {}.", this.id, player.getUserId());
                this.exitGame(player.getUserId());
                // After exitGame's "back to the room" packet, so the client
                // shows it on the game center screen.
                this.sendTo(player, new SnowStormGenericErrorComposer(SnowWarConstants.ERROR_IDLE_REMOVED));
                if (this.state != SnowWarGameState.RUNNING) {
                    return;
                }
            }
        }
    }

    private void sendTo(SnowWarGamePlayer player, MessageComposer composer) {
        if (player.getHabbo() != null && player.getHabbo().getClient() != null) {
            player.getHabbo().getClient().sendResponse(composer);
        }
    }

    // ========================================================================
    // Player actions (called from packet handlers)
    // ========================================================================

    /**
     * Applies a player action under the tick's monitor. Packet handlers run on
     * network threads; without this an action could change a player's state,
     * ammo or the snowball list halfway through a tick, so the server would
     * simulate (and checksum) something different from what it sends.
     */
    private void runAction(SnowWarGamePlayer player, Runnable action) {
        SnowWarGameTask current = this.task;
        if (this.state != SnowWarGameState.RUNNING || current == null) {
            return;
        }
        synchronized (current) {
            // The player may have left between the handler's lookup and here.
            if (this.state == SnowWarGameState.RUNNING && this.getPlayer(player.getUserId()) == player) {
                action.run();
            }
        }
    }

    public void handleWalk(SnowWarGamePlayer player, int worldX, int worldY) {
        player.markActive(System.currentTimeMillis());
        this.runAction(player, () -> this.applyWalk(player, worldX, worldY));
    }

    private void applyWalk(SnowWarGamePlayer player, int worldX, int worldY) {
        SnowWarAttributes attr = player.getAttributes();

        // AIR's HumanGameObject.changeMoveTarget cancels an in-progress
        // snowball build before applying the new movement target.
        if (attr.getActivityState() == SnowWarActivityState.CREATING_SNOWBALL) {
            attr.setActivityState(SnowWarActivityState.NORMAL);
            attr.setActivityTimer(0);
        }

        if (!attr.isWalkableState()) {
            return;
        }

        int tileX = SnowWarMath.convertToGameCoordinate(worldX);
        int tileY = SnowWarMath.convertToGameCoordinate(worldY);

        SnowWarTile targetTile = this.map.getTile(tileX, tileY);
        if (targetTile == null || targetTile.isBlocked()) {
            return;
        }

        attr.setGoalWorldCoordinates(new SnowWarPoint(worldX, worldY));
        attr.setWalkGoal(new SnowWarPoint(tileX, tileY));
        attr.setPathfindIterations(0);
        attr.setWalking(true);
    }

    public void handleThrowAtLocation(SnowWarGamePlayer player, int worldX, int worldY, int trajectory) {
        player.markActive(System.currentTimeMillis());
        // 0 = quick, 1 = short lob, 2 = long lob, 3 = AIR's adaptive throw.
        if (trajectory < SnowWarConstants.TRAJECTORY_QUICK || trajectory > SnowWarConstants.TRAJECTORY_DEFAULT) {
            return;
        }

        // worldX/worldY are client-supplied. Clamp them to the arena so a crafted
        // value can't overflow the (delta*delta) range-gate math below; an
        // off-map target would only ever hit nothing, but this keeps the maths
        // well-defined. throwAtPlayer uses the target's real position, so it
        // needs no clamp.
        int maxWorldX = this.map.getSizeX() * SnowWarConstants.TILE_SIZE;
        int maxWorldY = this.map.getSizeY() * SnowWarConstants.TILE_SIZE;
        int targetX = Math.max(0, Math.min(worldX, maxWorldX));
        int targetY = Math.max(0, Math.min(worldY, maxWorldY));

        this.runAction(player, () -> this.throwSnowball(player, targetX, targetY, trajectory));
    }

    public void handleThrowAtPlayer(SnowWarGamePlayer player, int targetObjectId, int trajectory) {
        player.markActive(System.currentTimeMillis());
        if (trajectory < SnowWarConstants.TRAJECTORY_QUICK || trajectory > SnowWarConstants.TRAJECTORY_DEFAULT) {
            return;
        }

        this.runAction(player, () -> {
            // Read the target's position under the lock, so it is the one the
            // next tick starts from.
            SnowWarGamePlayer target = this.getPlayerByObjectId(targetObjectId);
            if (target == null || !this.isOpponent(player, target)) {
                return;
            }

            SnowWarPoint targetWorld = target.getAttributes().getWorldPosition();
            this.throwSnowball(player, targetWorld.getX(), targetWorld.getY(), trajectory);
        });
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
        int maxRangeWorld =
                switch (trajectory) {
                    case SnowWarConstants.TRAJECTORY_QUICK -> SnowWarConstants.QUICK_THROW_MAX_RANGE;
                    case SnowWarConstants.TRAJECTORY_SHORT_LOB -> SnowWarConstants.SHORT_LOB_MAX_RANGE;
                    default -> SnowWarConstants.LONG_LOB_MAX_RANGE;
                };
        long deltaWorldX = (long) worldX - attr.getWorldPosition().getX();
        long deltaWorldY = (long) worldY - attr.getWorldPosition().getY();
        if ((deltaWorldX * deltaWorldX) + (deltaWorldY * deltaWorldY) > (long) maxRangeWorld * maxRangeWorld) {
            return;
        }

        if (attr.getSnowballCount().get() <= 0) {
            return;
        }

        if (attr.isWalking() && player.getAvatar() != null) {
            player.getAvatar().stopWalking();
        }

        int objectId = this.nextObjectId();
        SnowWarSnowballObject snowball = new SnowWarSnowballObject(
                objectId,
                this.map,
                player,
                attr.getWorldPosition().getX(),
                attr.getWorldPosition().getY(),
                worldX,
                worldY,
                trajectory);

        this.task.addSnowball(snowball);
        attr.getSnowballCount().decrementAndGet();
        attr.setLastThrowTime(System.currentTimeMillis());

        // Update facing direction.
        int currentWorldX = attr.getWorldPosition().getX();
        int currentWorldY = attr.getWorldPosition().getY();
        int direction = SnowWarMath.getAngleFromComponents(worldX - currentWorldX, worldY - currentWorldY);
        attr.setRotation(SnowWarMath.direction360To8(direction));

        this.task.queueEvent(new SnowWarLaunchSnowballEvent(
                objectId, player.getObjectId(), worldX, worldY, snowball.getTrajectory()));
    }

    public void handleCreateSnowball(SnowWarGamePlayer player) {
        player.markActive(System.currentTimeMillis());
        this.runAction(player, () -> this.applyCreateSnowball(player));
    }

    private void applyCreateSnowball(SnowWarGamePlayer player) {
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
        player.markActive(System.currentTimeMillis());

        // Anti-flood: chat is broadcast to every player, so rate-limit it.
        SnowWarAttributes chatAttr = player.getAttributes();
        long now = System.currentTimeMillis();
        if (chatAttr.getLastChatTime() + SnowWarConstants.CHAT_COOLDOWN_MS > now) {
            return;
        }
        chatAttr.setLastChatTime(now);

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
        if (player.isPlayAgain()) {
            return;
        }

        // Inside the restart window this counts as an official rematch; after
        // it, still honour the click as a plain fresh queue join so the
        // results-screen button never goes silently dead.
        boolean withinWindow = System.currentTimeMillis() <= this.restartWindowEndMillis;

        // A rematch inside the window waits for the window to end, so the
        // whole group goes back into the queue together with its teams (see
        // cleanup).
        if (withinWindow) {
            player.setPlayAgain(true);
            this.broadcast(new SnowStormUserRematchedComposer(player.getUserId()));
            return;
        }

        SnowWarManager.getInstance().clearUserGame(player.getUserId());
        SnowWarManager.getInstance().joinQueue(player.getHabbo());
    }

    public void sendFullGameStatus(SnowWarGamePlayer player) {
        // Anti-flood: serializing + sending the whole game state is expensive,
        // so rate-limit client-driven full-status requests per player.
        SnowWarAttributes attr = player.getAttributes();
        long now = System.currentTimeMillis();
        if (attr.getLastStatusRequestTime() + SnowWarConstants.STATUS_REQUEST_COOLDOWN_MS > now) {
            return;
        }
        attr.setLastStatusRequestTime(now);

        if (player.getHabbo().getClient() != null) {
            player.getHabbo().getClient().sendResponse(this.createFullGameStatusComposer());
        }
    }

    public SnowStormFullGameStatusComposer createFullGameStatusComposer() {
        List<SnowWarGameObject> objects = new ArrayList<>();

        objects.addAll(this.machines);
        this.piles.stream().filter(SnowWarPileObject::isAlive).forEach(objects::add);
        this.trees.stream().filter(SnowWarTreeObject::isAlive).forEach(objects::add);

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
