package com.eu.habbo.habbohotel.games.snowwar;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMap;
import com.eu.habbo.habbohotel.games.snowwar.mapping.SnowWarMapsManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.gamecenter.Game2GameCancelledComposer;
import com.eu.habbo.messages.outgoing.gamecenter.Game2InArenaQueueComposer;
import com.eu.habbo.messages.outgoing.gamecenter.Game2UserBlockedComposer;
import com.eu.habbo.messages.outgoing.gamecenter.GameCenterAccountInfoComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormEditorDataComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGamesInformationComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGamesLeftComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormGenericErrorComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormLobbyTeamsComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormQuePositionComposer;
import com.eu.habbo.messages.outgoing.snowwar.SnowStormStartLobbyCounterComposer;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton managing the SnowWar matchmaking queue and running games.
 */
public class SnowWarManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowWarManager.class);

    /**
     * permission_definitions key gating the custom arena builder (rank 7 by default).
     */
    public static final String BUILD_PERMISSION = "acc_snowwar_arena_build";

    /** Official arenas from the original GameCenter SnowWar implementation. */
    public static final List<Integer> ARENA_IDS = List.of(8, 9, 11);

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
    private final SnowWarArenaRepository arenas = new SnowWarArenaRepository(this::openConnection);
    private final SnowWarLeaderboardRepository leaderboard = new SnowWarLeaderboardRepository(this::openConnection);
    private final SnowWarTokenOfferRepository tokenOffers = new SnowWarTokenOfferRepository(this::openConnection);
    private final SnowWarGameCharges charges = new SnowWarGameCharges(
            this.tokenOffers, LocalDate::now, () -> Emulator.getConfig().getInt("gamecenter.games.free.daily", 10));
    private final SnowWarScoreWriter scoreWriter =
            new SnowWarScoreWriter(this.leaderboard::recordScores, (task, delayMs) -> schedule(task, delayMs) != null);

    // Per-user fixed-window packet counter for the generic SnowWar flood cap.
    // Value is [windowStartMillis, countInWindow]; guarded per-entry by the
    // entry object itself (see allowPacket).
    private final ConcurrentHashMap<Integer, long[]> packetWindows = new ConcurrentHashMap<>();

    // Last time a malformed SnowWar packet from a user was logged.
    private final ConcurrentHashMap<Integer, Long> malformedReports = new ConcurrentHashMap<>();
    private static final long MALFORMED_REPORT_INTERVAL_MS = 60_000L;

    private final AtomicInteger gameIdCounter = new AtomicInteger(0);
    private final AtomicInteger gamesPlayed = new AtomicInteger(0);

    private volatile boolean countdownRunning = false;
    // Game2InArenaQueue positions were sent and not cleared yet.
    private volatile boolean arenaQueueActive = false;
    private volatile int countdownSeconds = 0;
    private volatile int selectedArenaId = ARENA_IDS.get(0);
    private volatile List<SnowWarArenaDefinition> availableArenas;

    private static final long LEADERBOARD_CACHE_MS = 10_000L;
    private static final int LEADERBOARD_CACHE_MAX = 2_000;
    private final Map<String, CachedLeaderboard> leaderboardCache = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lobbySkillLevels = new ConcurrentHashMap<>();
    // Team a queued rematcher keeps in the next match (userId to teamId).
    private final Map<Integer, Integer> preferredTeams = new ConcurrentHashMap<>();
    // Wall-clock end of the leave block per user (AIR Game2UserBlocked).
    private final Map<Integer, Long> blockedUntil = new ConcurrentHashMap<>();

    /**
     * Per-user custom draft being edited. The map id comes from the server-side
     * arena catalogue, so a client cannot save over an official arena.
     */
    private final ConcurrentHashMap<Integer, Integer> editorArenas = new ConcurrentHashMap<>();

    private SnowWarManager() {}

    private static ScheduledFuture<?> schedule(Runnable task, long delayMs) {
        return Emulator.getThreading().run(task, delayMs);
    }

    private Connection openConnection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
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

    public SnowWarLeaderboardRepository.Page getLeaderboard(
            int viewerUserId, boolean weekly, boolean friendsOnly, int weekOffset, int startRank, int limit) {
        String key = "p|" + viewerUserId + "|" + weekly + "|" + friendsOnly + "|" + weekOffset + "|" + startRank + "|"
                + limit;
        return this.cachedLeaderboard(
                key, () -> this.leaderboard.load(viewerUserId, weekly, friendsOnly, weekOffset, startRank, limit));
    }

    public SnowWarLeaderboardRepository.GroupPage getGroupLeaderboard(
            int viewerUserId, boolean weekly, int weekOffset, int startRank, int limit) {
        String key = "g|" + viewerUserId + "|" + weekly + "|" + weekOffset + "|" + startRank + "|" + limit;
        return this.cachedLeaderboard(
                key, () -> this.leaderboard.loadGroups(viewerUserId, weekly, weekOffset, startRank, limit));
    }

    /**
     * Each leaderboard request runs aggregate SQL, so a page is served from a
     * short cache: repeated clicks or tab switches cost one query per
     * {@link #LEADERBOARD_CACHE_MS}. Unlike dropping the request, the client
     * always gets an answer.
     */
    @SuppressWarnings("unchecked")
    private <T> T cachedLeaderboard(String key, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        CachedLeaderboard cached = this.leaderboardCache.get(key);
        if (cached != null && now - cached.loadedAt() < LEADERBOARD_CACHE_MS) {
            return (T) cached.value();
        }
        T value = loader.get();
        if (this.leaderboardCache.size() >= LEADERBOARD_CACHE_MAX) {
            this.leaderboardCache.clear();
        }
        if (value != null) {
            this.leaderboardCache.put(key, new CachedLeaderboard(value, now));
        }
        return value;
    }

    private record CachedLeaderboard(Object value, long loadedAt) {}

    /**
     * Blocks starting a new game after leaving a running one (exit, idle
     * removal or disconnect).
     */
    void blockUser(int userId, int seconds) {
        if (seconds > 0) {
            this.blockedUntil.put(userId, System.currentTimeMillis() + seconds * 1000L);
        }
    }

    /** Seconds left on this user's leave block, 0 when not blocked. */
    public int getBlockSecondsLeft(int userId) {
        Long until = this.blockedUntil.get(userId);
        if (until == null) {
            return 0;
        }
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            this.blockedUntil.remove(userId, until);
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, (left + 999) / 1000);
    }

    public SnowWarTokenOfferRepository getTokenOffers() {
        return this.tokenOffers;
    }

    /**
     * AIR games_main footer: the free games left today plus the games bought
     * with tokens, {@code -1} meaning unlimited
     * ({@code gamecenter.games.free.daily = -1}). A match uses one game when
     * it starts (free games first).
     */
    public int getGamesLeft(int userId) {
        return this.charges.gamesLeft(userId);
    }

    /**
     * Captures the final scores now and saves them in the background (with
     * retries), so the end of a match never waits on the database.
     */
    void recordScores(List<SnowWarGamePlayer> players) {
        List<SnowWarLeaderboardRepository.ScoreRow> rows = new ArrayList<>();
        for (SnowWarGamePlayer player : players) {
            rows.add(new SnowWarLeaderboardRepository.ScoreRow(
                    player.getUserId(), player.getAttributes().getScore().get()));
        }
        this.scoreWriter.submit(SnowWarLeaderboardRepository.weekStart(0), rows);
    }

    /** All-time scores in the database, or {@code null} when they could not be read. */
    Map<Integer, Integer> loadSavedTotalScores(Collection<Integer> userIds) {
        return this.leaderboard.tryLoadTotalScores(userIds);
    }

    /** AIR skill level (1..30) per user id, derived from the all-time score. */
    public Map<Integer, Integer> getSkillLevels(Collection<Integer> userIds) {
        return this.skillLevelsFromSavedTotals(userIds, this.leaderboard.loadTotalScores(userIds));
    }

    /** Skill levels from saved totals plus the scores still waiting to be saved. */
    Map<Integer, Integer> skillLevelsFromSavedTotals(Collection<Integer> userIds, Map<Integer, Integer> savedTotals) {
        Map<Integer, Integer> pending = this.scoreWriter.pendingScores(userIds);
        Map<Integer, Integer> levels = new HashMap<>();
        for (Integer userId : userIds) {
            long total = (long) savedTotals.getOrDefault(userId, 0) + pending.getOrDefault(userId, 0);
            levels.put(userId, SnowWarSkillLevel.fromTotalScore((int) Math.min(Integer.MAX_VALUE, total)));
        }
        return levels;
    }

    public SnowWarArenaDefinition findArena(int arenaId) {
        return this.arenas.find(arenaId);
    }

    public boolean publishArena(int arenaId, int userId, String name) {
        return this.arenas.publish(arenaId, userId, name);
    }

    public void clearUserGame(int userId) {
        this.userGames.remove(userId);
        this.packetWindows.remove(userId);
        this.malformedReports.remove(userId);
        // A disconnecting editor must release the matchmaking lock and discard
        // an unpublished draft. Published arenas remain in the pool.
        if (this.clearEditor(userId) && this.editorArenas.isEmpty()) {
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
    /** True at most once a minute per user, so a misbehaving client cannot flood the log. */
    public boolean shouldReportMalformed(int userId) {
        long now = System.currentTimeMillis();
        Long previous = this.malformedReports.get(userId);
        if (previous != null && now - previous < MALFORMED_REPORT_INTERVAL_MS) {
            return false;
        }
        this.malformedReports.put(userId, now);
        return true;
    }

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

        // Left a running match not long ago: AIR blocks starting a new one.
        int blockSeconds = this.getBlockSecondsLeft(userId);
        if (blockSeconds > 0) {
            this.send(habbo, new Game2UserBlockedComposer(blockSeconds));
            return;
        }

        // No games left (a player who already paid for a forming match may rejoin).
        int gamesLeft = this.charges.hasPaid(userId) ? 1 : this.getGamesLeft(userId);
        if (gamesLeft == 0) {
            this.send(habbo, new SnowStormGenericErrorComposer(SnowWarConstants.ERROR_NO_TICKETS));
            this.send(habbo, new GameCenterAccountInfoComposer(0, 0));
            return;
        }

        boolean added;
        boolean newLobby;
        synchronized (this.queue) {
            newLobby = this.queue.isEmpty();
            added = this.queue.add(userId);
        }

        // A fresh skill level for this lobby (it may have changed since the
        // player's last match).
        if (added) {
            this.lobbySkillLevels.remove(userId);
        }

        // First player of a new lobby: start it on a random arena; the
        // leader can still pick another one.
        if (added && newLobby) {
            this.selectedArenaId = this.pickDefaultArenaId();
        }

        // Already queued: don't re-add or re-broadcast to everyone, but DO
        // re-send THIS user their current queue state. A client that lost the
        // waiting screen (reload, earlier desync) would otherwise look stuck -
        // its repeat Join gets no response and nothing happens on screen.
        if (!added) {
            this.sendGamesInformation(habbo);
            this.sendQueuePositionTo(habbo);
            this.broadcastLobbyTeams();
            return;
        }

        this.send(habbo, new SnowStormGamesLeftComposer(gamesLeft));
        this.sendGamesInformation(habbo);

        this.broadcastQueuePositions();
        this.maybeStartCountdown();
    }

    /** Sends the live queue state and per-user arena-builder permission. */
    public void sendGamesInformation(Habbo habbo) {
        if (habbo == null) {
            return;
        }
        this.send(
                habbo,
                new SnowStormGamesInformationComposer(
                        this.getQueueSize(),
                        this.gamesPlayed.get(),
                        this.getMinimumPlayers(),
                        habbo.hasPermission(BUILD_PERMISSION)));
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
        this.preferredTeams.remove(userId);

        // Paid for a match that then fell short of players: give it back.
        if (this.charges.refundIfPaid(userId)) {
            this.send(habbo, new GameCenterAccountInfoComposer(0, this.getGamesLeft(userId)));
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

    /**
     * The first queued player leads the next match and may choose its arena.
     * Invalid, unavailable, and non-leader requests are ignored.
     */
    public void selectArena(Habbo habbo, int arenaId) {
        if (habbo == null
                || this.getAvailableArenas().stream().noneMatch(arena -> arena.id() == arenaId)
                || SnowWarMapsManager.getMap(arenaId) == null) {
            return;
        }

        synchronized (this.queue) {
            if (this.queue.isEmpty()
                    || this.queue.iterator().next() != habbo.getHabboInfo().getId()) {
                return;
            }
            this.selectedArenaId = arenaId;
        }

        this.broadcastLobbyTeams();
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
            Habbo habbo = this.onlineHabbo(userId);
            if (habbo != null) {
                this.send(habbo, new SnowStormQuePositionComposer(position, queued.size()));
            }
            position++;
        }

        // Re-send the provisional team line-up on every queue change so the
        // client's "getting ready" screen shows the waiting players (and their
        // teams) live while below the minimum, not just during the countdown.
        this.broadcastLobbyTeams();

        // Positions shift when someone joins or leaves while the arenas are full.
        if (this.arenaQueueActive && !this.countdownRunning && this.games.size() >= this.getMaxConcurrentGames()) {
            this.sendArenaQueuePositions();
        }
    }

    /**
     * AIR Game2InArenaQueue: enough players wait but every arena is busy.
     * Each queued player gets the number of full matches ahead of them,
     * counted from 1; -1 means "not waiting for an arena".
     */
    private void sendArenaQueuePositions() {
        if (this.getQueueSize() < this.getMinimumPlayers()) {
            this.clearArenaQueuePositions();
            return;
        }
        int matchSize = Math.max(1, this.getMaximumMatchPlayers());
        int index = 0;
        for (Integer userId : this.getQueueSnapshot()) {
            Habbo habbo = this.onlineHabbo(userId);
            if (habbo != null) {
                this.send(habbo, new Game2InArenaQueueComposer((index / matchSize) + 1));
            }
            index++;
        }
        this.arenaQueueActive = true;
    }

    private Habbo onlineHabbo(int userId) {
        return Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
    }

    private void clearArenaQueuePositions() {
        if (!this.arenaQueueActive) {
            return;
        }
        this.arenaQueueActive = false;
        this.broadcastToQueue(new Game2InArenaQueueComposer(-1));
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
        if (!this.editorArenas.isEmpty()) {
            return;
        }

        if (this.getQueueSize() < this.getMinimumPlayers()) {
            return;
        }

        // Concurrent session cap (default 1): while it is reached the queue
        // holds and matching resumes from onGameFinished. The waiting players
        // see their place in the arena queue (AIR Game2InArenaQueue).
        if (this.games.size() >= this.getMaxConcurrentGames()) {
            this.sendArenaQueuePositions();
            return;
        }

        this.clearArenaQueuePositions();
        this.countdownRunning = true;
        this.countdownSeconds = this.getLobbyCountdownSeconds();

        this.broadcastToQueue(new SnowStormStartLobbyCounterComposer(this.countdownSeconds));

        schedule(this::countdownTick, 1000);
    }

    private void countdownTick() {
        if (!this.countdownRunning) {
            return;
        }

        // Drop offline users from the queue before evaluating.
        List<Integer> offline = new ArrayList<>();
        synchronized (this.queue) {
            this.queue.removeIf(userId -> {
                boolean gone = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId) == null;
                if (gone) {
                    offline.add(userId);
                }
                return gone;
            });
        }
        // Gone after paying for a match that fell short: give the game back.
        for (Integer userId : offline) {
            this.charges.refundIfPaid(userId);
            this.preferredTeams.remove(userId);
        }

        if (this.getQueueSize() < this.getMinimumPlayers()) {
            this.countdownRunning = false;
            // AIR Game2GameCancelled (3493): the countdown had already started
            // and the lobby fell apart, so the waiting clients drop back to the
            // hub instead of sitting on a frozen counter.
            this.broadcastToQueue(new Game2GameCancelledComposer());
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
        schedule(this::countdownTick, 1000);
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
        int leaderUserId = roster.isEmpty() ? 0 : roster.get(0).getHabboInfo().getId();
        List<Integer> rosterIds = new ArrayList<>();
        for (Habbo habbo : roster) {
            rosterIds.add(habbo.getHabboInfo().getId());
        }
        this.broadcastToQueue(new SnowStormLobbyTeamsComposer(
                roster,
                2,
                leaderUserId,
                this.selectedArenaId,
                this.getAvailableArenas(),
                this.lobbySkillLevels(rosterIds),
                SnowWarTeams.assign(rosterIds, this.preferredTeams, 2)));
    }

    /** The teams queued rematchers keep (read by SnowWarGame when the match forms). */
    public Map<Integer, Integer> getPreferredTeams() {
        return this.preferredTeams;
    }

    /**
     * End of a rematch window: the players who pressed rematch go back into
     * the queue together, at its head, and keep their teams in the next
     * match.
     */
    void enqueueRematch(List<Habbo> rematchers, Map<Integer, Integer> teams) {
        List<Integer> group = new ArrayList<>();
        for (Habbo habbo : rematchers) {
            int userId = habbo.getHabboInfo().getId();
            Integer team = teams.get(userId);
            if (team != null) {
                this.preferredTeams.put(userId, team);
            }
            this.joinQueue(habbo);
            if (this.isQueued(userId)) {
                group.add(userId);
            } else {
                this.preferredTeams.remove(userId);
            }
        }
        if (group.isEmpty()) {
            return;
        }
        synchronized (this.queue) {
            List<Integer> rest = new ArrayList<>(this.queue);
            rest.removeAll(group);
            this.queue.clear();
            this.queue.addAll(group);
            this.queue.addAll(rest);
        }
        this.broadcastQueuePositions();
        this.broadcastLobbyTeams();
    }

    /**
     * The lobby line-up is re-sent every countdown second, so the skill levels
     * are read once when a player joins the queue (their entry is dropped on
     * join, see joinQueue) instead of from the database on every broadcast.
     * Players no longer on the line-up are pruned.
     */
    private Map<Integer, Integer> lobbySkillLevels(List<Integer> rosterIds) {
        this.lobbySkillLevels.keySet().retainAll(rosterIds);
        List<Integer> missing = new ArrayList<>();
        for (Integer userId : rosterIds) {
            if (!this.lobbySkillLevels.containsKey(userId)) {
                missing.add(userId);
            }
        }
        if (!missing.isEmpty()) {
            this.lobbySkillLevels.putAll(this.getSkillLevels(missing));
        }
        Map<Integer, Integer> levels = new HashMap<>();
        for (Integer userId : rosterIds) {
            levels.put(userId, this.lobbySkillLevels.getOrDefault(userId, 1));
        }
        return levels;
    }

    // ========================================================================
    // Match creation / teardown
    // ========================================================================

    private void createMatch() {
        // Never form a match while the arena is being edited.
        if (!this.editorArenas.isEmpty()) {
            this.broadcastQueuePositions();
            return;
        }

        // Re-check the cap: a game may have started while we counted down.
        if (this.games.size() >= this.getMaxConcurrentGames()) {
            this.broadcastQueuePositions();
            return;
        }

        List<Habbo> participants = new ArrayList<>();
        int arenaId;

        synchronized (this.queue) {
            arenaId = this.selectedArenaId;
            List<Integer> queued = new ArrayList<>(this.queue);

            for (Integer userId : queued) {
                if (participants.size() >= this.getMaximumMatchPlayers()) {
                    break;
                }

                Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
                if (habbo == null) {
                    this.queue.remove(userId);
                    this.charges.refundIfPaid(userId);
                    this.preferredTeams.remove(userId);
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

            // The remaining queue forms a new lobby with a new leader.
            this.selectedArenaId = this.pickDefaultArenaId();
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

        // Each player uses one game now. The players are out of the queue
        // while this runs, so nobody can join or be charged twice meanwhile.
        List<Integer> charged = new ArrayList<>();
        List<Habbo> paying = new ArrayList<>();
        for (Habbo habbo : participants) {
            int userId = habbo.getHabboInfo().getId();
            SnowWarGameAllowance.ConsumeResult result = this.charges.charge(userId);
            if (result.allowed()) {
                paying.add(habbo);
                if (result.charged()) {
                    charged.add(userId);
                }
                continue;
            }
            this.send(
                    habbo,
                    new SnowStormGenericErrorComposer(
                            result == SnowWarGameAllowance.ConsumeResult.NO_GAMES
                                    ? SnowWarConstants.ERROR_NO_TICKETS
                                    : SnowWarConstants.ERROR_INTERNAL));
        }
        participants = paying;

        if (participants.size() < this.getMinimumPlayers()) {
            // Someone had no games left: the paid players wait for the next
            // match (marked paid, so they are not charged again).
            synchronized (this.queue) {
                for (Habbo habbo : participants) {
                    this.queue.add(habbo.getHabboInfo().getId());
                }
            }
            this.broadcastQueuePositions();
            return;
        }

        SnowWarGame game = new SnowWarGame(this.gameIdCounter.incrementAndGet(), arenaId, participants);
        this.games.put(game.getId(), game);

        List<Integer> participantIds = new ArrayList<>();
        for (Habbo habbo : participants) {
            participantIds.add(habbo.getHabboInfo().getId());
            this.userGames.put(habbo.getHabboInfo().getId(), game);
        }
        this.charges.started(participantIds);
        // The match used the rematch teams; they do not carry over again.
        participantIds.forEach(this.preferredTeams::remove);

        this.gamesPlayed.incrementAndGet();
        this.broadcastQueuePositions();

        LOGGER.info("SnowWar game {} created with {} players.", game.getId(), participants.size());

        game.start();

        // The arena failed to load: nobody played, so give the games back.
        if (game.getState() == SnowWarGameState.ENDED) {
            this.charges.refund(charged);
        }

        for (Habbo habbo : participants) {
            this.send(
                    habbo,
                    new GameCenterAccountInfoComposer(
                            0, this.getGamesLeft(habbo.getHabboInfo().getId())));
        }
    }

    // ========================================================================
    // Arena editor
    // ========================================================================

    /**
     * Opens a fresh custom-arena draft cloned from Duckie's editor template.
     * Official arenas are never handed to the save path.
     */
    public void enterEditor(Habbo habbo) {
        if (habbo == null || !habbo.hasPermission(BUILD_PERMISSION)) {
            return;
        }

        int userId = habbo.getHabboInfo().getId();
        SnowWarArenaDefinition draft = this.arenas.createDraft(userId);
        if (draft == null) {
            return;
        }

        SnowWarMap map = SnowWarMapsManager.getMap(draft.id());
        if (map == null) {
            this.arenas.discardDraft(draft.id(), userId);
            return;
        }

        SnowWarGame game = this.getGameByUserId(userId);
        if (game != null) {
            game.exitGame(userId);
        }
        this.leaveQueue(habbo);

        Integer previousDraft = this.editorArenas.put(userId, draft.id());
        if (previousDraft != null) {
            this.arenas.discardDraft(previousDraft, userId);
        }
        this.countdownRunning = false;
        this.endAllGames();
        this.send(habbo, new SnowStormEditorDataComposer(map, draft.name()));
    }

    /**
     * A user leaves the arena editor. Once the last editor is out, matchmaking
     * resumes.
     */
    public void exitEditor(Habbo habbo) {
        if (habbo == null) {
            return;
        }
        if (this.clearEditor(habbo.getHabboInfo().getId()) && this.editorArenas.isEmpty()) {
            this.maybeStartCountdown();
        }
    }

    public boolean isEditing() {
        return !this.editorArenas.isEmpty();
    }

    public boolean canSaveEditorArena(int userId, int arenaId) {
        return this.editorArenas.getOrDefault(userId, -1) == arenaId;
    }

    public void onArenaPublished() {
        this.availableArenas = this.arenas.findActive();
        this.broadcastLobbyTeams();
    }

    /**
     * Default arena for a new lobby: a random official arena (any active one
     * when none is official), so lobbies don't all play the first arena.
     */
    private int pickDefaultArenaId() {
        List<SnowWarArenaDefinition> arenas = this.getAvailableArenas();
        List<SnowWarArenaDefinition> official =
                arenas.stream().filter(SnowWarArenaDefinition::official).toList();
        List<SnowWarArenaDefinition> pool = official.isEmpty() ? arenas : official;
        if (pool.isEmpty()) {
            return ARENA_IDS.get(0);
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size())).id();
    }

    private List<SnowWarArenaDefinition> getAvailableArenas() {
        List<SnowWarArenaDefinition> arenas = this.availableArenas;
        if (arenas == null) {
            arenas = this.arenas.findActive();
            this.availableArenas = arenas;
        }
        return arenas;
    }

    private boolean clearEditor(int userId) {
        Integer arenaId = this.editorArenas.remove(userId);
        if (arenaId == null) {
            return false;
        }
        this.arenas.discardDraft(arenaId, userId);
        return true;
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
        try (java.sql.Connection connection = this.openConnection();
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
