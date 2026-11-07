package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A table of the game hall: its players in their seats (people and robots), the game they play, whose move it is and
 * for how long, the robots' moves, and the score of the table round after round. The first player still here hosts:
 * they add robots, choose the seats (poker) and start. A player out of time gets a move played for them; three
 * times, and they lose the game. Not thread-safe: the manager locks the table.
 */
public final class HallTable {

    public enum State {
        WAITING,
        PLAYING,
        FINISHED
    }

    static final int MAX_TIMEOUTS = 3;
    private static final String[] BOT_NAMES = {"Renard Rusé", "Gédéon", "Mistigri", "Pompon", "Bérénice", "Filou"};
    private static final String[] BOT_FIGURES = {
        "hr-828-45.hd-180-1.ch-3215-92.lg-3023-110.sh-305-62",
        "hr-515-33.hd-600-1.ch-635-73.lg-716-82.sh-735-68",
        "hr-893-48.hd-180-2.ch-3030-81.lg-275-82.sh-290-91",
        "hr-890-45.hd-600-1.ch-665-92.lg-720-82.sh-907-62",
        "hr-170-42.hd-190-8.ch-215-66.lg-280-110.sh-300-64",
        "hr-545-33.hd-600-10.ch-635-66.lg-696-82.sh-725-64"
    };
    private static final String[] BOT_GENDERS = {"M", "F", "M", "F", "M", "F"};

    /** Somebody at the table; a robot has a negative id. */
    public static final class Seat {
        final int userId;
        final String name;
        final String figure;
        final String gender;
        final boolean bot;
        final long joined;
        long lastSeen;
        int timeouts;
        int wins;
        boolean gone;

        Seat(int userId, String name, String figure, String gender, boolean bot, long joined, long now) {
            this.userId = userId;
            this.name = name;
            this.figure = figure;
            this.gender = gender;
            this.bot = bot;
            this.joined = joined;
            this.lastSeen = now;
        }

        public int userId() {
            return this.userId;
        }

        public boolean bot() {
            return this.bot;
        }

        public long lastSeen() {
            return this.lastSeen;
        }

        public int wins() {
            return this.wins;
        }
    }

    private final int id;
    private final HallGames.Kind kind;
    private final Random random;
    private final long turnMs;
    private final List<Seat> seats = new ArrayList<>();
    private final Map<Integer, Long> waitingSince = new HashMap<>();
    private final Map<Integer, Long> botAt = new HashMap<>();
    private int size;
    private boolean bots;
    private State state = State.WAITING;
    private HallGame game;
    private int round;
    private long joins;
    private int nextBotId = -1;
    private boolean recorded = true;
    private String note = "";
    private final List<int[]> results = new ArrayList<>();

    public HallTable(int id, HallGames.Kind kind, int size, boolean bots, long turnMs, Random random) {
        this.id = id;
        this.kind = kind;
        this.size = Math.max(kind.minSeats(), Math.min(kind.maxSeats(), size));
        this.bots = bots;
        this.turnMs = turnMs;
        this.random = random;
    }

    // ---- The seats

    public void join(int userId, String name, String figure, String gender, long now) {
        if (this.seat(userId) != null) return;
        if (this.state == State.PLAYING) throw new HallException("already_started");
        if (this.seats.size() >= this.size) throw new HallException("table_full");

        this.seats.add(new Seat(userId, name, figure, gender, false, ++this.joins, now));
    }

    public void addBot(int hostId, long now) {
        this.requireHost(hostId);
        if (!this.bots) throw new HallException("no_bots");
        if (this.state == State.PLAYING) throw new HallException("already_started");
        if (this.seats.size() >= this.size) throw new HallException("table_full");

        for (String name : BOT_NAMES) {
            if (this.seats.stream().noneMatch(seat -> seat.name.equals(name))) {
                int look = this.random.nextInt(BOT_FIGURES.length);

                this.seats.add(new Seat(
                        this.nextBotId--, name, BOT_FIGURES[look], BOT_GENDERS[look], true, ++this.joins, now));
                return;
            }
        }

        throw new HallException("table_full");
    }

    public void removeBot(int hostId) {
        this.requireHost(hostId);
        if (this.state == State.PLAYING) throw new HallException("already_started");

        for (int index = this.seats.size() - 1; index >= 0; index--) {
            if (this.seats.get(index).bot) {
                this.seats.remove(index);
                return;
            }
        }

        throw new HallException("no_bot");
    }

    public void setOptions(int hostId, int size, boolean bots) {
        this.requireHost(hostId);
        if (this.state == State.PLAYING) throw new HallException("already_started");

        this.size = Math.max(this.kind.minSeats(), Math.min(this.kind.maxSeats(), size));
        this.bots = bots;

        if (!bots) this.seats.removeIf(seat -> seat.bot);

        while (this.seats.size() > this.size) {
            Seat gone = null;

            for (Seat seat : this.seats) {
                if (seat.bot) gone = seat;
            }

            this.seats.remove(gone != null ? gone : this.seats.get(this.seats.size() - 1));
        }
    }

    public void leave(int userId, long now) {
        Seat seat = this.seat(userId);

        if (seat == null) return;

        if (this.state == State.PLAYING) {
            this.game.forfeit(this.seats.indexOf(seat));
            this.note = "left:" + seat.name;
            this.afterMove(now);
        }

        if (this.state == State.PLAYING) {
            // A game for more (poker) goes on without them: their seat stays, folded, until the next game.
            seat.gone = true;
            return;
        }

        this.seats.remove(seat);

        // The last board does not fit the seats any more.
        if (this.state == State.FINISHED) {
            this.state = State.WAITING;
            this.game = null;
        }
    }

    public void seen(int userId, long now) {
        Seat seat = this.seat(userId);

        if (seat != null) seat.lastSeen = now;
    }

    public void start(int hostId, long now) {
        this.requireHost(hostId);
        if (this.state == State.PLAYING) throw new HallException("already_started");
        if (this.seats.size() < this.kind.minSeats()) throw new HallException("not_enough_players");

        this.seats.removeIf(seat -> seat.gone);

        if (this.seats.size() < this.kind.minSeats()) throw new HallException("not_enough_players");

        // Each new game, the seats turn round: who began last time plays second.
        if (this.round > 0) Collections.rotate(this.seats, 1);

        this.game = HallGames.create(this.kind.name());
        this.game.start(this.seats.size(), this.random);
        this.state = State.PLAYING;
        this.round++;
        this.recorded = false;
        this.note = "";
        this.waitingSince.clear();
        this.botAt.clear();

        for (Seat seat : this.seats) seat.timeouts = 0;

        this.advance(now);
    }

    // ---- The moves

    public void move(int userId, JsonObject move, long now) {
        Seat seat = this.seat(userId);

        if (seat == null) throw new HallException("no_table");
        if (this.state != State.PLAYING) throw new HallException("not_started");

        int index = this.seats.indexOf(seat);

        if (!this.game.waitingFor(index)) throw new HallException("not_your_turn");

        this.game.play(index, move);
        seat.timeouts = 0;
        this.afterMove(now);
    }

    /** The time goes by: robots play when they have thought, a player out of time gets a move played for them. */
    public void advance(long now) {
        if (this.state == State.PLAYING && this.game.finished()) this.afterMove(now);

        for (int guard = 0; guard < 8 && this.state == State.PLAYING; guard++) {
            boolean moved = false;

            for (int index = 0; index < this.seats.size() && this.state == State.PLAYING; index++) {
                Seat seat = this.seats.get(index);

                if (!this.game.waitingFor(index)) {
                    this.waitingSince.remove(index);
                    this.botAt.remove(index);
                    continue;
                }

                long since = this.waitingSince.computeIfAbsent(index, key -> now);

                if (seat.bot) {
                    long at = this.botAt.computeIfAbsent(index, key -> since + 700 + this.random.nextInt(1300));

                    if (now >= at) {
                        this.game.bot(index, this.random);
                        moved = true;
                    }
                } else if (now - since >= this.turnMs) {
                    seat.timeouts++;

                    if (seat.timeouts >= MAX_TIMEOUTS) {
                        this.note = "timeout:" + seat.name;
                        this.game.forfeit(index);
                    } else {
                        this.game.bot(index, this.random);
                    }

                    moved = true;
                }

                if (moved) {
                    this.afterMove(now);
                    break;
                }
            }

            if (!moved) return;
        }
    }

    private void afterMove(long now) {
        this.waitingSince.clear();
        this.botAt.clear();

        if (!this.game.finished()) return;

        this.state = State.FINISHED;
        this.results.clear();

        for (int winner : this.game.winners()) this.seats.get(winner).wins++;

        for (int index = 0; index < this.seats.size(); index++) {
            Seat seat = this.seats.get(index);

            if (!seat.bot)
                this.results.add(new int[] {seat.userId, this.game.winners().contains(index) ? 1 : 0});
        }
    }

    /** The people of the last game: user id, 1 if they won. */
    public List<int[]> results() {
        return this.results;
    }

    public boolean takeRecord() {
        if (this.state != State.FINISHED || this.recorded) return false;

        this.recorded = true;
        return true;
    }

    // ---- What a player sees

    public JsonObject summary() {
        JsonObject summary = new JsonObject();
        Seat host = this.seat(this.hostId());

        summary.addProperty("id", this.id);
        summary.addProperty("game", this.kind.name());
        summary.addProperty("host", host == null ? "" : host.name);
        summary.addProperty(
                "players", (int) this.seats.stream().filter(seat -> !seat.gone).count());
        summary.addProperty("size", this.size);
        summary.addProperty("bots", this.bots);
        summary.addProperty("state", this.state.name().toLowerCase());
        return summary;
    }

    public JsonObject view(int userId, long now) {
        JsonObject view = this.summary();
        Seat me = this.seat(userId);
        int index = me == null ? -1 : this.seats.indexOf(me);

        view.addProperty("hostId", this.hostId());
        view.addProperty("youAreHost", this.hostId() == userId);
        view.addProperty("you", index);
        view.addProperty("round", this.round);
        view.addProperty("note", this.note);
        view.addProperty("turnMs", this.turnMs);

        JsonArray players = new JsonArray();

        for (int seat = 0; seat < this.seats.size(); seat++) {
            Seat entry = this.seats.get(seat);
            JsonObject player = new JsonObject();
            Long since = this.waitingSince.get(seat);

            player.addProperty("userId", entry.userId);
            player.addProperty("name", entry.name);
            player.addProperty("figure", entry.figure);
            player.addProperty("gender", entry.gender);
            player.addProperty("bot", entry.bot);
            player.addProperty("wins", entry.wins);
            player.addProperty("gone", entry.gone);
            player.addProperty("waiting", this.state == State.PLAYING && this.game.waitingFor(seat));
            player.addProperty(
                    "timeLeft", since == null || entry.bot ? -1L : Math.max(0L, this.turnMs - (now - since)));
            players.add(player);
        }

        view.add("players", players);

        if (this.game != null) {
            view.add("board", this.game.view(index));

            JsonArray winners = new JsonArray();

            if (this.state == State.FINISHED) {
                for (int winner : this.game.winners()) winners.add(winner);
            }

            view.add("winners", winners);
        }

        return view;
    }

    // ---- Helpers

    private void requireHost(int userId) {
        if (this.hostId() != userId) throw new HallException("not_host");
    }

    public Seat seat(int userId) {
        for (Seat seat : this.seats) {
            if (seat.userId == userId && !seat.gone) return seat;
        }

        return null;
    }

    /** The host: the first player who came and is still here (robots never host). */
    public int hostId() {
        Seat host = null;

        for (Seat seat : this.seats) {
            if (!seat.bot && !seat.gone && (host == null || seat.joined < host.joined)) host = seat;
        }

        return host == null ? 0 : host.userId;
    }

    public boolean hasPlayers() {
        return this.hostId() != 0;
    }

    public int id() {
        return this.id;
    }

    public HallGames.Kind kind() {
        return this.kind;
    }

    public State state() {
        return this.state;
    }

    public List<Seat> seats() {
        return Collections.unmodifiableList(this.seats);
    }

    public HallGame game() {
        return this.game;
    }

    /** The seats that won the last game (empty: a draw, or no game yet). */
    public List<Integer> winners() {
        return this.state == State.FINISHED && this.game != null ? this.game.winners() : List.of();
    }
}
