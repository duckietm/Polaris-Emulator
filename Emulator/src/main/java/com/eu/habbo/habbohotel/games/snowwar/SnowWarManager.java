package com.eu.habbo.habbohotel.games.snowwar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMapsManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGamesInformationComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGamesLeftComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGenericErrorComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormQuePositionComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormStartLobbyCounterComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton managing the SnowWar matchmaking queue and running games.
 */
public class SnowWarManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowWarManager.class);

    /**
     * permission_definitions key gating the arena editor (rank 7 by default).
     */
    public static final String EDIT_PERMISSION = "acc_snowwar_edit";

    // Eager, immutable singleton: construction is cheap (collections only,
    // config is read lazily per use) and a final field keeps the class free
    // of mutable statics (FrozenArchitectureBaselineTest).
    private static final SnowWarManager INSTANCE = new SnowWarManager();

    public static SnowWarManager getInstance() {
        return INSTANCE;
    }

    /**
     * Queued user ids, insertion ordered. Guarded by itself.
     */
    private final LinkedHashSet<Integer> queue = new LinkedHashSet<>();

    private final ConcurrentHashMap<Integer, SnowWarGame> games = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, SnowWarGame> userGames = new ConcurrentHashMap<>();

    private final AtomicInteger gameIdCounter = new AtomicInteger(0);
    private final AtomicInteger gamesPlayed = new AtomicInteger(0);

    private volatile boolean countdownRunning = false;
    private volatile int countdownSeconds = 0;

    private SnowWarManager() {
    }

    public boolean isEnabled() {
        return Emulator.getConfig().getBoolean("gamecenter.snowwar.enabled", true);
    }

    public int getMinimumPlayers() {
        return Math.max(1, Emulator.getConfig().getInt("gamecenter.snowwar.players.min", 2));
    }

    private int getMaximumMatchPlayers() {
        return Math.max(2, Emulator.getConfig().getInt("gamecenter.snowwar.queue.match.max", 8));
    }

    private int getMaxConcurrentGames() {
        return Math.max(1, Emulator.getConfig().getInt("gamecenter.snowwar.games.max.concurrent", 1));
    }

    private int getLobbyCountdownSeconds() {
        return Math.max(1, Emulator.getConfig().getInt("gamecenter.snowwar.game.start.time", 15));
    }

    public SnowWarGame getGameByUserId(int userId) {
        return this.userGames.get(userId);
    }

    public void clearUserGame(int userId) {
        this.userGames.remove(userId);
    }

    public void clearUserGameIfMatches(int userId, SnowWarGame game) {
        this.userGames.remove(userId, game);
    }

    // ========================================================================
    // Queue handling
    // ========================================================================

    public void joinQueue(Habbo habbo) {
        if (habbo == null) {
            return;
        }

        if (!this.isEnabled()) {
            this.send(habbo, new SnowStormGenericErrorComposer(SnowWarConstants.ERROR_INTERNAL));
            return;
        }

        int userId = habbo.getHabboInfo().getId();

        if (this.userGames.containsKey(userId)) {
            this.send(habbo, new SnowStormGenericErrorComposer(SnowWarConstants.ERROR_ALREADY_IN_GAME));
            return;
        }

        synchronized (this.queue) {
            this.queue.add(userId);
        }

        this.send(habbo, new SnowStormGamesLeftComposer(-1));
        this.send(habbo, new SnowStormGamesInformationComposer(this.getQueueSize(), this.gamesPlayed.get()));

        this.broadcastQueuePositions();
        this.maybeStartCountdown();
    }

    public void leaveQueue(Habbo habbo) {
        if (habbo == null) {
            return;
        }

        boolean removed;
        synchronized (this.queue) {
            removed = this.queue.remove(habbo.getHabboInfo().getId());
        }

        if (removed) {
            this.broadcastQueuePositions();
        }
    }

    public boolean isQueued(int userId) {
        synchronized (this.queue) {
            return this.queue.contains(userId);
        }
    }

    private int getQueueSize() {
        synchronized (this.queue) {
            return this.queue.size();
        }
    }

    private List<Integer> getQueueSnapshot() {
        synchronized (this.queue) {
            return new ArrayList<>(this.queue);
        }
    }

    private void broadcastQueuePositions() {
        List<Integer> queued = this.getQueueSnapshot();
        int position = 1;

        for (Integer userId : queued) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (habbo != null) {
                this.send(habbo, new SnowStormQuePositionComposer(position, queued.size()));
            }
            position++;
        }
    }

    private void broadcastToQueue(com.eu.habbo.messages.outgoing.MessageComposer composer) {
        com.eu.habbo.messages.ServerMessage message = composer.compose();

        for (Integer userId : this.getQueueSnapshot()) {
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (habbo != null && habbo.getClient() != null) {
                habbo.getClient().sendResponse(message);
            }
        }
    }

    // ========================================================================
    // Lobby countdown
    // ========================================================================

    private synchronized void maybeStartCountdown() {
        if (this.countdownRunning) {
            return;
        }

        if (this.getQueueSize() < this.getMinimumPlayers()) {
            return;
        }

        // Concurrent session cap (default 1): while it is reached the queue
        // holds and matching resumes from onGameFinished.
        if (this.games.size() >= this.getMaxConcurrentGames()) {
            return;
        }

        this.countdownRunning = true;
        this.countdownSeconds = this.getLobbyCountdownSeconds();

        this.broadcastToQueue(new SnowStormStartLobbyCounterComposer(this.countdownSeconds));

        Emulator.getThreading().run(this::countdownTick, 1000);
    }

    private void countdownTick() {
        if (!this.countdownRunning) {
            return;
        }

        // Drop offline users from the queue before evaluating.
        synchronized (this.queue) {
            this.queue.removeIf(userId -> Emulator.getGameEnvironment().getHabboManager().getHabbo(userId) == null);
        }

        if (this.getQueueSize() < this.getMinimumPlayers()) {
            this.countdownRunning = false;
            this.broadcastQueuePositions();
            return;
        }

        this.countdownSeconds--;

        if (this.countdownSeconds <= 0) {
            this.countdownRunning = false;
            this.createMatch();

            // Keep matching while enough players remain queued.
            this.maybeStartCountdown();
            return;
        }

        this.broadcastToQueue(new SnowStormStartLobbyCounterComposer(this.countdownSeconds));
        Emulator.getThreading().run(this::countdownTick, 1000);
    }

    // ========================================================================
    // Match creation / teardown
    // ========================================================================

    private void createMatch() {
        // Re-check the cap: a game may have started while we counted down.
        if (this.games.size() >= this.getMaxConcurrentGames()) {
            this.broadcastQueuePositions();
            return;
        }

        List<Habbo> participants = new ArrayList<>();

        synchronized (this.queue) {
            List<Integer> queued = new ArrayList<>(this.queue);

            for (Integer userId : queued) {
                if (participants.size() >= this.getMaximumMatchPlayers()) {
                    break;
                }

                Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
                if (habbo == null) {
                    this.queue.remove(userId);
                    continue;
                }

                participants.add(habbo);
                this.queue.remove(userId);
            }
        }

        if (participants.size() < this.getMinimumPlayers()) {
            // Not enough online players after filtering; put them back.
            synchronized (this.queue) {
                for (Habbo habbo : participants) {
                    this.queue.add(habbo.getHabboInfo().getId());
                }
            }
            this.broadcastQueuePositions();
            return;
        }

        SnowWarGame game = new SnowWarGame(this.gameIdCounter.incrementAndGet(), 1, participants);
        this.games.put(game.getId(), game);

        for (Habbo habbo : participants) {
            this.userGames.put(habbo.getHabboInfo().getId(), game);
        }

        this.gamesPlayed.incrementAndGet();
        this.broadcastQueuePositions();

        LOGGER.info("SnowWar game {} created with {} players.", game.getId(), participants.size());

        game.start();
    }

    /**
     * The arena editor is a normal room built on the SnowWar room model, so
     * furniture can be placed with the standard room tools. The first room
     * using the model is reused; otherwise one is created for the editor.
     */
    public Room getOrCreateEditorRoom(Habbo habbo, int mapId) {
        String modelName = SnowWarMapsManager.getModelName(mapId);

        int roomId = 0;
        try (java.sql.Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                java.sql.PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM rooms WHERE model = ? ORDER BY id LIMIT 1")) {
            statement.setString(1, modelName);
            try (java.sql.ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    roomId = set.getInt("id");
                }
            }
        } catch (java.sql.SQLException e) {
            LOGGER.error("Failed to look up the SnowWar editor room.", e);
            return null;
        }

        if (roomId > 0) {
            return Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId);
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().createRoom(
                habbo.getHabboInfo().getId(),
                habbo.getHabboInfo().getUsername(),
                "SnowStorm Arena Editor",
                "Place furniture to design the SnowStorm arena, then use :snowwarsave to publish it.",
                modelName,
                25,
                0,
                0);

        if (room == null) {
            LOGGER.error("Failed to create the SnowWar editor room with model '{}'. Is the room_models row present?", modelName);
        }

        return room;
    }

    public void onGameFinished(SnowWarGame game) {
        this.games.remove(game.getId());
        this.userGames.entrySet().removeIf(entry -> entry.getValue() == game);

        // A session slot freed up - resume matching for waiting players.
        this.maybeStartCountdown();
    }

    private void send(Habbo habbo, com.eu.habbo.messages.outgoing.MessageComposer composer) {
        if (habbo.getClient() != null) {
            habbo.getClient().sendResponse(composer);
        }
    }
}
