package polaris.hotel.games.hall;

import polaris.Emulator;
import polaris.core.Scheduler;
import polaris.hotel.games.center.GameCenter;
import polaris.hotel.users.User;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Habbi's game hall (tic-tac-toe, four in a row, battleships, chess, poker): the tables (kept in memory), the clock
 * that runs the robots and the players' time, the results written to `hall_stats`, and what each player sees
 * (`/api/hall`).
 */
public final class HallManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(HallManager.class);
    private static final HallManager INSTANCE = new HallManager();
    /** A player whose window has not asked for the table for this long has left it. */
    private static final long GONE_MS = 30_000L;

    private final Map<Integer, HallTable> tables = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> tableOfUser = new ConcurrentHashMap<>();
    private final AtomicInteger nextTableId = new AtomicInteger(1);
    private final AtomicBoolean clockStarted = new AtomicBoolean();
    private final Random random = new Random();

    private HallManager() {}

    public static HallManager get() {
        INSTANCE.startClock();
        return INSTANCE;
    }

    // ---- The emulator's services, each reached in one place

    private static boolean enabled() {
        return Emulator.getConfig().getBoolean("hall.enabled", true);
    }

    private static int setting(String key, int fallback) {
        return Emulator.getConfig().getInt(key, fallback);
    }

    private static User onlineUser(int userId) {
        return Emulator.getGameEnvironment().getUserManager().getUser(userId);
    }

    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    /** The players at a table now, by the Game Center's game ("hall" for the game hall's own games). */
    public Map<String, Integer> playersNow() {
        Map<String, Integer> counts = new HashMap<>();

        for (Integer tableId : this.tableOfUser.values()) {
            HallTable table = this.tables.get(tableId);

            if (table != null) counts.merge(GameCenter.keyOfHall(table.kind().name()), 1, Integer::sum);
        }

        return counts;
    }

    // ---- Actions

    /** Runs a player's action and answers what they see now. */
    public JsonObject act(int userId, String action, JsonObject body) {
        if (!enabled()) throw new HallException("disabled");

        long now = System.currentTimeMillis();

        switch (action) {
            case "create" -> this.create(userId, body, now);
            case "join" -> this.join(userId, intOf(body, "tableId", 0), now);
            case "leave" -> this.leave(userId);
            default -> this.onTable(userId, action, body, now);
        }

        return this.view(userId, stringOf(body, "game"));
    }

    private void create(int userId, JsonObject body, long now) {
        HallGames.Kind kind = HallGames.kind(stringOf(body, "game"));

        this.leave(userId);

        if (this.tables.size() >= setting("hall.tables.max", 40)) throw new HallException("too_many_games");

        if (GameCenter.get().gamesLeft(userId, GameCenter.keyOfHall(kind.name())) == 0)
            throw new HallException("no_games_left");

        User user = this.player(userId);
        HallTable table = new HallTable(
                this.nextTableId.getAndIncrement(),
                kind,
                intOf(body, "size", kind.seats()),
                boolOf(body, "bots", true),
                setting("hall.turn.seconds", 60) * 1000L,
                this.random);

        table.join(
                userId, user.getUserInfo().getUsername(), user.getUserInfo().getLook(), gender(user), now);
        this.tables.put(table.id(), table);
        this.tableOfUser.put(userId, table.id());
    }

    private void join(int userId, int tableId, long now) {
        HallTable table = this.tables.get(tableId);

        if (table == null) throw new HallException("no_table");
        if (Integer.valueOf(tableId).equals(this.tableOfUser.get(userId))) return;

        if (GameCenter.get().gamesLeft(userId, GameCenter.keyOfHall(table.kind().name())) == 0)
            throw new HallException("no_games_left");

        User user = this.player(userId);

        this.leave(userId);

        synchronized (table) {
            table.join(
                    userId,
                    user.getUserInfo().getUsername(),
                    user.getUserInfo().getLook(),
                    gender(user),
                    now);
        }

        this.tableOfUser.put(userId, tableId);
    }

    public void leave(int userId) {
        Integer tableId = this.tableOfUser.remove(userId);
        HallTable table = tableId == null ? null : this.tables.get(tableId);

        if (table == null) return;

        synchronized (table) {
            table.leave(userId, System.currentTimeMillis());
            this.recordIfFinished(table);
            if (!table.hasPlayers()) this.tables.remove(table.id());
        }
    }

    private void onTable(int userId, String action, JsonObject body, long now) {
        HallTable table = this.tableOf(userId);

        if (table == null) throw new HallException("no_table");

        synchronized (table) {
            table.advance(now);

            switch (action) {
                case "options" -> table.setOptions(userId, intOf(body, "size", 2), boolOf(body, "bots", true));
                case "bot" -> table.addBot(userId, now);
                case "bot/remove" -> table.removeBot(userId);
                case "start" -> {
                    List<Integer> humans = humansOf(table.seats());
                    String key = GameCenter.keyOfHall(table.kind().name());

                    if (GameCenter.get().firstWithoutGames(humans, key) != 0) throw new HallException("no_games_left");

                    table.start(userId, now);
                    GameCenter.get().charge(humans, key);
                }
                case "move" ->
                    table.move(userId, body != null && body.has("move") ? body.getAsJsonObject("move") : null, now);
                default -> throw new HallException("unknown_action");
            }

            this.recordIfFinished(table);
        }
    }

    /** The players of a game, robots aside (the Game Center counts their games and scores). */
    private static List<Integer> humansOf(List<HallTable.Seat> seats) {
        List<Integer> humans = new ArrayList<>();

        for (HallTable.Seat seat : seats) {
            if (!seat.bot()) humans.add(seat.userId());
        }

        return humans;
    }

    // ---- The clock

    private void startClock() {
        if (!this.clockStarted.compareAndSet(false, true)) return;

        new Scheduler(1) {
            @Override
            protected void tick() {
                HallManager.this.tick();
            }
        }.restart();
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (HallTable table : new ArrayList<>(this.tables.values())) {
            try {
                synchronized (table) {
                    for (HallTable.Seat seat : new ArrayList<>(table.seats())) {
                        if (!seat.bot() && now - seat.lastSeen() > GONE_MS) {
                            this.tableOfUser.remove(seat.userId(), table.id());
                            table.leave(seat.userId(), now);
                        }
                    }

                    table.advance(now);
                    this.recordIfFinished(table);

                    if (!table.hasPlayers()) this.tables.remove(table.id());
                }
            } catch (HallException e) {
                LOGGER.debug("[hall] table {}: {}", table.id(), e.code());
            } catch (Exception e) {
                LOGGER.error("[hall] table {} failed, closing it", table.id(), e);
                this.tables.remove(table.id());
                this.tableOfUser.values().removeIf(id -> id == table.id());
            }
        }
    }

    // ---- Results

    private void recordIfFinished(HallTable table) {
        if (!table.takeRecord()) return;

        List<int[]> players = new ArrayList<>(table.results());

        GameCenter.get()
                .record(
                        GameCenter.keyOfHall(table.kind().name()),
                        players.stream().map(player -> player[0]).toList(),
                        players.stream()
                                .filter(player -> player[1] == 1)
                                .map(player -> player[0])
                                .toList());

        if (players.isEmpty()) return;

        String game = table.kind().name();

        Emulator.getThreading().run(() -> this.writeStats(game, players));
    }

    private void writeStats(String game, List<int[]> players) {
        String sql = "INSERT INTO hall_stats (user_id, game, games, wins) VALUES (?, ?, 1, ?) "
                + "ON DUPLICATE KEY UPDATE games = games + 1, wins = wins + VALUES(wins)";

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int[] player : players) {
                statement.setInt(1, player[0]);
                statement.setString(2, game);
                statement.setInt(3, player[1]);
                statement.addBatch();
            }

            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("[hall] could not write the results", e);
        }
    }

    // ---- What a player sees

    /** The player's table, and the open tables of a game (all of them when no game is given). */
    public JsonObject view(int userId, String game) {
        JsonObject view = new JsonObject();
        HallTable table = this.tableOf(userId);
        long now = System.currentTimeMillis();

        view.addProperty("enabled", enabled());
        view.addProperty("userId", userId);

        if (table != null) {
            synchronized (table) {
                table.seen(userId, now);
                table.advance(now);
                this.recordIfFinished(table);
                view.add("table", table.view(userId, now));
            }
        }

        JsonArray list = new JsonArray();

        for (HallTable other : this.tables.values()) {
            if (game != null && !game.isEmpty() && !other.kind().name().equals(game)) continue;

            synchronized (other) {
                list.add(other.summary());
            }
        }

        view.add("tables", list);
        return view;
    }

    // ---- Helpers

    private HallTable tableOf(int userId) {
        Integer tableId = this.tableOfUser.get(userId);

        return tableId == null ? null : this.tables.get(tableId);
    }

    private User player(int userId) {
        User user = onlineUser(userId);

        if (user == null) throw new HallException("offline");

        return user;
    }

    private static String gender(User user) {
        return user.getUserInfo().getGender() == null
                ? "M"
                : user.getUserInfo().getGender().name();
    }

    private static int intOf(JsonObject body, String key, int fallback) {
        try {
            return body != null && body.has(key) ? body.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean boolOf(JsonObject body, String key, boolean fallback) {
        try {
            return body != null && body.has(key) ? body.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String stringOf(JsonObject body, String key) {
        try {
            return body != null && body.has(key) ? body.get(key).getAsString() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }
}
