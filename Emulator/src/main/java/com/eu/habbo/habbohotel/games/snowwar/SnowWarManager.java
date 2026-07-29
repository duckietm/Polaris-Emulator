package com.eu.habbo.habbohotel.games.snowwar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMap;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMapsManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.snowwar.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    // Per-user fixed-window packet counter for the generic SnowWar flood cap.
    // Value is [windowStartMillis, countInWindow]; guarded per-entry by the
    // entry object itself (see allowPacket).
    private final ConcurrentHashMap<Integer, long[]> packetWindows = new ConcurrentHashMap<>();

    private final AtomicInteger gameIdCounter = new AtomicInteger(0);
    private final AtomicInteger gamesPlayed = new AtomicInteger(0);

    private volatile boolean countdownRunning = false;
    private volatile int countdownSeconds = 0;

    /**
     * Users (acc_snowwar_edit) currently in the arena editor. While non-empty,
     * matchmaking is frozen and no game may run; cleared as editors leave
     * (exitEditor / disconnect), which resumes matching.
     */
    private final Set<Integer> editors = ConcurrentHashMap.newKeySet();

    /** Arena map the editor edits (games run on map 1). */
    private static final int EDITOR_MAP_ID = 1;

    private SnowWarManager() {}

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
        this.packetWindows.remove(userId);
        // A disconnecting editor must release the matchmaking lock.
        if (this.editors.remove(userId) && this.editors.isEmpty()) {
            this.maybeStartCountdown();
        }
    }

    public void clearUserGameIfMatches(int userId, SnowWarGame game) {
        this.userGames.remove(userId, game);
    }

    /**
     * Generic per-user flood cap across every SnowWar packet. Fixed-window
     * counter: at most PACKET_FLOOD_MAX_PER_WINDOW packets per
     * PACKET_FLOOD_WINDOW_MS. Returns false when the packet should be dropped.
     * This is a blanket backstop on top of the per-action cooldowns, so a mix
     * of packet types can't be spun in a tight loop.
     */
    public boolean allowPacket(int userId) {
        long now = System.currentTimeMillis();
        long[] window = this.packetWindows.computeIfAbsent(userId, id -> new long[] {now, 0});
        synchronized (window) {
            if (now - window[0] > SnowWarConstants.PACKET_FLOOD_WINDOW_MS) {
                window[0] = now;
                window[1] = 1;
                return true;
            }
            window[1]++;
            return window[1] <= SnowWarConstants.PACKET_FLOOD_MAX_PER_WINDOW;
        }
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

        boolean added;
        synchronized (this.queue) {
            added = this.queue.add(userId);
        }

        // Already queued: don't re-add or re-broadcast to everyone, but DO
        // re-send THIS user their current queue state. A client that lost the
        // waiting screen (reload, earlier desync) would otherwise look stuck -
        // its repeat Join gets no response and nothing happens on screen.
        if (!added) {
            this.send(
                habbo,
                new SnowStormGamesInformationComposer(
                    this.getQueueSize(),
                    this.gamesPlayed.get(),
                    this.getMinimumPlayers(),
                    habbo.hasPermission(EDIT_PERMISSION)));
            this.sendQueuePositionTo(habbo);
            this.broadcastLobbyTeams();
            return;
        }

        this.send(habbo, new SnowStormGamesLeftComposer(-1));
        this.send(
            habbo,
            new SnowStormGamesInformationComposer(
                this.getQueueSize(),
                this.gamesPlayed.get(),
                this.getMinimumPlayers(),
                habbo.hasPermission(EDIT_PERMISSION)));

        this.broadcastQueuePositions();
        this.maybeStartCountdown();
    }

    public void leaveQueue(Habbo habbo) {
        if (habbo == null) {
            return;
        }

        int userId = habbo.getHabboInfo().getId();
        boolean removed;
        synchronized (this.queue) {
            removed = this.queue.remove(userId);
        }

        this.packetWindows.remove(userId);

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

    private void sendQueuePositionTo(Habbo habbo) {
        List<Integer> queued = this.getQueueSnapshot();
        int index = queued.indexOf(habbo.getHabboInfo().getId());
        if (index < 0) {
            return;
        }
        this.send(habbo, new SnowStormQuePositionComposer(index + 1, queued.size()));
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

        // Re-send the provisional team line-up on every queue change so the
        // client's "getting ready" screen shows the waiting players (and their
        // teams) live while below the minimum, not just during the countdown.
        this.broadcastLobbyTeams();
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

        // No games may start while anyone is editing the arena.
        if (!this.editors.isEmpty()) {
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
            this.queue.removeIf(
                userId -> Emulator.getGameEnvironment().getHabboManager().getHabbo(userId) == null);
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
        // Re-send the (possibly changed) line-up each tick so a player who
        // joined/left the queue during the countdown is reflected live.
        this.broadcastLobbyTeams();
        Emulator.getThreading().run(this::countdownTick, 1000);
    }

    /**
     * Build the provisional match line-up from the current queue snapshot and
     * broadcast it so queued clients can show the pre-match "getting ready"
     * team screen. Team split mirrors {@link SnowWarGame}'s round-robin
     * assignment at creation (index % teamCount).
     */
    private void broadcastLobbyTeams() {
        List<Habbo> roster = new ArrayList<>();
        for (Integer userId : this.getQueueSnapshot()) {
            if (roster.size() >= this.getMaximumMatchPlayers()) {
                break;
            }
            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
            if (habbo != null) {
                roster.add(habbo);
            }
        }
        // teamCount 2 matches SnowWarGame (Red / Blue).
        this.broadcastToQueue(new SnowStormLobbyTeamsComposer(roster, 2));
    }

    // ========================================================================
    // Match creation / teardown
    // ========================================================================

    private void createMatch() {
        // Never form a match while the arena is being edited.
        if (!this.editors.isEmpty()) {
            this.broadcastQueuePositions();
            return;
        }

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

                // Never pull a user who is already in a game into a second one
                // (would overwrite userGames and orphan their first game).
                if (this.userGames.containsKey(userId)) {
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

    // ========================================================================
    // Arena editor
    // ========================================================================

    /**
     * A permitted user opens the arena editor. Takes them out of any game/queue,
     * freezes matchmaking (no game may run while anyone edits), ends every
     * running game so nothing ticks behind the editor, and hands the editor the
     * current arena map to edit.
     */
    public void enterEditor(Habbo habbo) {
        if (habbo == null || !habbo.hasPermission(EDIT_PERMISSION)) {
            return;
        }

        int userId = habbo.getHabboInfo().getId();

        SnowWarGame game = this.getGameByUserId(userId);
        if (game != null) {
            game.exitGame(userId);
        }
        this.leaveQueue(habbo);

        this.editors.add(userId);
        this.countdownRunning = false;
        this.endAllGames();

        SnowWarMap map = SnowWarMapsManager.getMap(EDITOR_MAP_ID);
        if (map != null) {
            this.send(habbo, new SnowStormEditorDataComposer(map));
        }
    }

    /**
     * A user leaves the arena editor. Once the last editor is out, matchmaking
     * resumes.
     */
    public void exitEditor(Habbo habbo) {
        if (habbo == null) {
            return;
        }
        if (this.editors.remove(habbo.getHabboInfo().getId()) && this.editors.isEmpty()) {
            this.maybeStartCountdown();
        }
    }

    public boolean isEditing() {
        return !this.editors.isEmpty();
    }

    private void endAllGames() {
        for (SnowWarGame game : new ArrayList<>(this.games.values())) {
            game.endGame();
        }
    }

    /**
     * The arena editor is a normal room built on the SnowWar room model, so
     * furniture can be placed with the standard room tools. The first room
     * using the model is reused; otherwise one is created for the editor.
     */
    public Room getOrCreateEditorRoom(Habbo habbo, int mapId) {
        String modelName = SnowWarMapsManager.getModelName(mapId);

        int roomId = 0;
        try (java.sql.Connection connection =
                 Emulator.getDatabase().getDataSource().getConnection();
             java.sql.PreparedStatement statement =
                 connection.prepareStatement("SELECT id FROM rooms WHERE model = ? ORDER BY id LIMIT 1")) {
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

        Room room = Emulator.getGameEnvironment()
            .getRoomManager()
            .createRoom(
                habbo.getHabboInfo().getId(),
                habbo.getHabboInfo().getUsername(),
                "SnowStorm Arena Editor",
                "Place furniture to design the SnowStorm arena, then use :snowwarsave to publish it.",
                modelName,
                25,
                0,
                0);

        if (room == null) {
            LOGGER.error(
                "Failed to create the SnowWar editor room with model '{}'. Is the room_models row present?",
                modelName);
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
