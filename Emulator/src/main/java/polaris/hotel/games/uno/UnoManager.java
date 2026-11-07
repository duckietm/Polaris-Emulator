package polaris.hotel.games.uno;

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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UNO: the tables (kept in memory), who sits where, the clock that plays the robots and the turns that run
 * out, the winners written to `uno_stats`, and what each player sees (`/api/uno`).
 */
public final class UnoManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnoManager.class);
    private static final UnoManager INSTANCE = new UnoManager();
    /** A player whose window has not asked for the table for this long has left it. */
    private static final long GONE_MS = 30_000L;

    private final Map<Integer, UnoTable> tables = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> tableOfUser = new ConcurrentHashMap<>();
    private final AtomicInteger nextTableId = new AtomicInteger(1);
    private final AtomicBoolean clockStarted = new AtomicBoolean();
    private final Random random = new Random();

    private UnoManager() {}

    public static UnoManager get() {
        INSTANCE.startClock();
        return INSTANCE;
    }

    // ---- The emulator's services, each reached in one place

    private static boolean enabled() {
        return Emulator.getConfig().getBoolean("uno.enabled", true);
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

    /** The players at a table now, for the Game Center's list. */
    public int playersNow() {
        return this.tableOfUser.size();
    }

    // ---- Actions

    /** Runs a player's action and answers what they see now. */
    public JsonObject act(int userId, String action, JsonObject body) {
        if (!enabled()) throw new UnoException("disabled");

        long now = System.currentTimeMillis();

        switch (action) {
            case "create" -> this.create(userId, body, now);
            case "join" -> this.join(userId, intOf(body, "tableId"), intOf(body, "chair", -1), now);
            case "leave" -> this.leave(userId);
            default -> this.onTable(userId, action, body, now);
        }

        return this.view(userId);
    }

    private void create(int userId, JsonObject body, long now) {
        this.leave(userId);

        if (this.tables.size() >= setting("uno.tables.max", 20)) throw new UnoException("too_many_tables");

        if (GameCenter.get().gamesLeft(userId, "uno") == 0) throw new UnoException("no_games_left");

        User user = this.player(userId);
        UnoRules rules = UnoRules.from(
                rulesOf(body), UnoRules.standard(4, setting("uno.turn.seconds", 25)), setting("uno.players.max", 6));
        UnoTable table = new UnoTable(this.nextTableId.getAndIncrement(), rules, this.random);

        table.join(
                userId,
                user.getUserInfo().getUsername(),
                user.getUserInfo().getLook(),
                gender(user),
                intOf(body, "chair", -1),
                now);
        this.tables.put(table.id(), table);
        this.tableOfUser.put(userId, table.id());
    }

    private void join(int userId, int tableId, int chair, long now) {
        UnoTable table = this.tables.get(tableId);

        if (table == null) throw new UnoException("no_table");
        if (Integer.valueOf(tableId).equals(this.tableOfUser.get(userId))) return;

        if (GameCenter.get().gamesLeft(userId, "uno") == 0) throw new UnoException("no_games_left");

        User user = this.player(userId);

        this.leave(userId);

        synchronized (table) {
            table.join(
                    userId,
                    user.getUserInfo().getUsername(),
                    user.getUserInfo().getLook(),
                    gender(user),
                    chair,
                    now);
        }

        this.tableOfUser.put(userId, tableId);
    }

    public void leave(int userId) {
        Integer tableId = this.tableOfUser.remove(userId);
        UnoTable table = tableId == null ? null : this.tables.get(tableId);

        if (table == null) return;

        synchronized (table) {
            boolean playing = table.state() == UnoTable.State.PLAYING;

            table.leave(userId);
            if (playing) this.recordIfFinished(table);
            if (!table.hasPlayers()) this.tables.remove(table.id());
        }
    }

    private void onTable(int userId, String action, JsonObject body, long now) {
        UnoTable table = this.tableOf(userId);

        if (table == null) throw new UnoException("no_table");

        synchronized (table) {
            boolean playing = table.state() == UnoTable.State.PLAYING;

            switch (action) {
                case "bot" -> table.addBot(userId, intOf(body, "chair", -1), now);
                case "sit" -> table.sit(userId, intOf(body, "chair", -1));
                case "rules" ->
                    table.setRules(userId, UnoRules.from(rulesOf(body), table.rules(), setting("uno.players.max", 6)));
                case "bot/remove" -> table.removeBot(userId, intOf(body, "userId"));
                case "start" -> {
                    List<Integer> humans = humansOf(table.seats());
                    String key = "uno";

                    if (GameCenter.get().firstWithoutGames(humans, key) != 0) throw new UnoException("no_games_left");

                    table.start(userId, now);
                    GameCenter.get().charge(humans, key);
                }
                case "play" -> table.play(userId, stringOf(body, "card"), firstChar(stringOf(body, "colour")), now);
                case "draw" -> table.draw(userId, now);
                case "pass" -> table.pass(userId, now);
                case "uno" -> table.declare(userId);
                case "catch" -> table.catchUno(userId, intOf(body, "userId"));
                default -> throw new UnoException("unknown_action");
            }

            if (playing) this.recordIfFinished(table);
        }
    }

    /** The players of a game, robots aside (the Game Center counts their games and scores). */
    private static List<Integer> humansOf(List<UnoTable.Seat> seats) {
        List<Integer> humans = new ArrayList<>();

        for (UnoTable.Seat seat : seats) {
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
                UnoManager.this.tick();
            }
        }.restart();
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (UnoTable table : new ArrayList<>(this.tables.values())) {
            try {
                synchronized (table) {
                    boolean playing = table.state() == UnoTable.State.PLAYING;

                    for (UnoTable.Seat seat : new ArrayList<>(table.seats())) {
                        if (!seat.bot() && now - seat.lastSeen > GONE_MS) {
                            this.tableOfUser.remove(seat.userId(), table.id());
                            table.leave(seat.userId());
                        }
                    }

                    for (int removed : table.tick(now)) this.tableOfUser.remove(removed, table.id());

                    if (playing) this.recordIfFinished(table);
                    if (!table.hasPlayers()) this.tables.remove(table.id());
                }
            } catch (UnoException e) {
                LOGGER.debug("[uno] table {}: {}", table.id(), e.code());
            } catch (Exception e) {
                LOGGER.error("[uno] table {} failed, closing it", table.id(), e);
                this.tables.remove(table.id());
                this.tableOfUser.values().removeIf(id -> id == table.id());
            }
        }
    }

    // ---- Winners

    private void recordIfFinished(UnoTable table) {
        if (table.state() != UnoTable.State.FINISHED) return;

        List<Integer> players = new ArrayList<>();

        for (UnoTable.Seat seat : table.seats()) {
            if (!seat.bot()) players.add(seat.userId());
        }

        int winner = table.winnerId();

        GameCenter.get().record("uno", players, List.of(winner));

        Emulator.getThreading().run(() -> this.writeStats(players, winner));
    }

    private void writeStats(List<Integer> players, int winner) {
        String sql = "INSERT INTO uno_stats (user_id, games, wins) VALUES (?, 1, ?) "
                + "ON DUPLICATE KEY UPDATE games = games + 1, wins = wins + VALUES(wins)";

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int userId : players) {
                statement.setInt(1, userId);
                statement.setInt(2, userId == winner ? 1 : 0);
                statement.addBatch();
            }

            statement.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("[uno] could not write the scores", e);
        }
    }

    // ---- What a player sees

    public JsonObject view(int userId) {
        JsonObject view = new JsonObject();
        UnoTable table = this.tableOf(userId);

        view.addProperty("enabled", enabled());
        view.addProperty("userId", userId);

        if (table != null) {
            synchronized (table) {
                table.seen(userId, System.currentTimeMillis());
                view.add("table", UnoViews.table(table, userId, System.currentTimeMillis()));
            }
        }

        JsonArray list = new JsonArray();

        for (UnoTable other : this.tables.values()) {
            synchronized (other) {
                list.add(UnoViews.summary(other));
            }
        }

        view.add("tables", list);
        return view;
    }

    // ---- Helpers

    private UnoTable tableOf(int userId) {
        Integer tableId = this.tableOfUser.get(userId);

        return tableId == null ? null : this.tables.get(tableId);
    }

    private User player(int userId) {
        User user = onlineUser(userId);

        if (user == null) throw new UnoException("offline");

        return user;
    }

    private static String gender(User user) {
        return user.getUserInfo().getGender() == null
                ? "M"
                : user.getUserInfo().getGender().name();
    }

    private static int intOf(JsonObject body, String key) {
        return intOf(body, key, 0);
    }

    private static int intOf(JsonObject body, String key, int fallback) {
        try {
            return body != null && body.has(key) ? body.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static JsonObject rulesOf(JsonObject body) {
        return body != null && body.has("rules") && body.get("rules").isJsonObject()
                ? body.getAsJsonObject("rules")
                : null;
    }

    private static String stringOf(JsonObject body, String key) {
        try {
            return body != null && body.has(key) ? body.get(key).getAsString() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static char firstChar(String value) {
        return value == null || value.isEmpty() ? ' ' : value.charAt(0);
    }
}
