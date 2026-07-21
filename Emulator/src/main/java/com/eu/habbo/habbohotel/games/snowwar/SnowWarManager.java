package com.eu.habbo.habbohotel.games.snowwar;

import com.eu.habbo.Emulator;
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

    private static volatile SnowWarManager instance;

    public static SnowWarManager getInstance() {
        if (instance == null) {
            synchronized (SnowWarManager.class) {
                if (instance == null) {
                    instance = new SnowWarManager();
                }
            }
        }
        return instance;
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

    private int getMinimumPlayers() {
        return Math.max(1, Emulator.getConfig().getInt("gamecenter.snowwar.players.min", 2));
    }

    private int getMaximumMatchPlayers() {
        return Math.max(2, Emulator.getConfig().getInt("gamecenter.snowwar.queue.match.max", 8));
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

    public void onGameFinished(SnowWarGame game) {
        this.games.remove(game.getId());
        this.userGames.entrySet().removeIf(entry -> entry.getValue() == game);
    }

    private void send(Habbo habbo, com.eu.habbo.messages.outgoing.MessageComposer composer) {
        if (habbo.getClient() != null) {
            habbo.getClient().sendResponse(composer);
        }
    }
}
