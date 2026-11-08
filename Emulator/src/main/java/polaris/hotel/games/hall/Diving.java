package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The diving board of the Lido (Habbo's Shockwave hotel): a diving contest. In turn, each player climbs the tower and
 * dives; their window plays the original jump (run with A and D, jump with space, the seven dives in the air) and
 * records the key of each tenth of a second, as the Lido did. Everybody then watches the dive, played again from
 * those keys, and the others hold up their mark (0 to 10). A dive is worth the average of its marks; after every
 * round, the most points win.
 *
 * <p>The game keeps the jump's keys as sent (a friendly game): it only checks their form. Robots dive with keys
 * drawn here and mark a dive by its moves.
 */
public final class Diving implements HallGame {

    static final long INTRO_MS = 3000L;
    /** The time on the tower; the window runs on its own after 30 s, and here a dive is made for a sleeper. */
    static final long JUMP_MS = 45_000L;
    /** The dive played again: its ticks, the fall into the water and the splash. */
    static final long SHOW_EXTRA_MS = 5500L;

    static final long TICK_MS = 100L;
    static final long VOTE_MS = 12_000L;
    static final long SCORE_MS = 4000L;
    static final int MAX_KEYS = 600;
    private static final Pattern KEYS = Pattern.compile("[0adwezsx ]+");

    private enum Phase {
        INTRO,
        JUMP,
        SHOW,
        VOTE,
        SCORE,
        END
    }

    private int seats;
    private int rounds;
    private int round;
    /** The order of the dives: the seats of a round, one after the other. */
    private int jumper;

    private Phase phase = Phase.INTRO;
    private long phaseStart = -1;
    private long clock;
    private String keys = "";
    private int[] votes = new int[0];
    private double[] totals = new double[0];
    private double[] last = new double[0];
    private int[] dives = new int[0];
    private boolean[] out = new boolean[0];
    private double average;
    private boolean finished;
    private List<Integer> winners = List.of();

    @Override
    public void start(int seats, Random random) {
        this.seats = seats;
        this.rounds = seats <= 4 ? 2 : 1;
        this.round = 0;
        this.jumper = 0;
        this.phase = Phase.INTRO;
        this.phaseStart = -1;
        this.keys = "";
        this.votes = new int[seats];
        this.totals = new double[seats];
        this.last = new double[seats];
        this.dives = new int[seats];
        this.out = new boolean[seats];
        this.average = 0;
        this.finished = false;
        this.winners = List.of();
    }

    @Override
    public void tick(long now, Random random) {
        this.clock = now;

        if (this.finished) return;

        if (this.phaseStart < 0) {
            this.phaseStart = now;
            return;
        }

        long elapsed = now - this.phaseStart;

        switch (this.phase) {
            case INTRO -> {
                if (elapsed >= INTRO_MS) this.climb(this.phaseStart + INTRO_MS);
            }
            case JUMP -> {
                // A sleeper on the tower: a dive is made for them.
                if (elapsed >= JUMP_MS) this.dive(robotKeys(random, 0), now);
            }
            case SHOW -> {
                if (elapsed >= this.showMs()) this.enter(Phase.VOTE, this.phaseStart + this.showMs());
            }
            case VOTE -> {
                if (elapsed >= VOTE_MS || this.everybodyVoted()) this.score(Math.min(now, this.phaseStart + VOTE_MS));
            }
            case SCORE -> {
                if (elapsed >= SCORE_MS) this.next(this.phaseStart + SCORE_MS);
            }
            default -> {}
        }
    }

    private long showMs() {
        return (this.keys.length() + 14) * TICK_MS + SHOW_EXTRA_MS;
    }

    private void enter(Phase next, long at) {
        this.phase = next;
        this.phaseStart = at;
    }

    /** The next diver who is still there climbs the tower; after the last one of the last round, the end. */
    private void climb(long at) {
        while (this.round < this.rounds && this.out[this.jumper]) this.advanceJumper();

        if (this.round >= this.rounds) {
            this.end();
            return;
        }

        this.keys = "";
        this.average = 0;

        for (int seat = 0; seat < this.seats; seat++) this.votes[seat] = -1;

        this.enter(Phase.JUMP, at);
    }

    private void advanceJumper() {
        this.jumper++;

        if (this.jumper >= this.seats) {
            this.jumper = 0;
            this.round++;
        }
    }

    private void next(long at) {
        this.advanceJumper();
        this.climb(at);
    }

    private void dive(String keys, long at) {
        this.keys = keys;
        this.enter(Phase.SHOW, at);
    }

    private boolean everybodyVoted() {
        for (int seat = 0; seat < this.seats; seat++) {
            if (this.isJudge(seat) && this.votes[seat] < 0) return false;
        }

        return true;
    }

    private boolean isJudge(int seat) {
        return seat != this.jumper && !this.out[seat];
    }

    private void score(long at) {
        int sum = 0;
        int count = 0;

        for (int seat = 0; seat < this.seats; seat++) {
            if (seat != this.jumper && this.votes[seat] >= 0) {
                sum += this.votes[seat];
                count++;
            }
        }

        this.average = count == 0 ? 0 : Math.round(10.0 * sum / count) / 10.0;
        this.totals[this.jumper] += this.average;
        this.last[this.jumper] = this.average;
        this.dives[this.jumper]++;
        this.enter(Phase.SCORE, at);
    }

    private void end() {
        double best = -1;
        List<Integer> top = new ArrayList<>();

        for (int seat = 0; seat < this.seats; seat++) {
            if (this.out[seat]) continue;

            double total = Math.round(this.totals[seat] * 10) / 10.0;

            int order = Double.compare(total, best);

            if (order > 0) {
                best = total;
                top.clear();
            }

            if (order >= 0) top.add(seat);
        }

        this.winners = best > 0 ? top : List.of();
        this.phase = Phase.END;
        this.finished = true;
    }

    @Override
    public void play(int seat, JsonObject move) {
        if (!this.waitingFor(seat)) throw new HallException("not_your_turn");

        if (this.phase == Phase.JUMP) {
            String sent = move != null && move.has("keys") ? move.get("keys").getAsString() : "";

            if (sent.isEmpty()
                    || sent.length() > MAX_KEYS
                    || !KEYS.matcher(sent).matches()) throw new HallException("bad_move");

            this.dive(sent, this.clock);
            return;
        }

        int mark = TurnGame.intOf(move, "mark");

        if (mark < 0 || mark > 10) throw new HallException("bad_move");

        this.votes[seat] = mark;
    }

    @Override
    public boolean waitingFor(int seat) {
        if (this.finished || seat >= this.seats || this.out[seat]) return false;

        if (this.phase == Phase.JUMP) return seat == this.jumper;

        return this.phase == Phase.VOTE && this.isJudge(seat) && this.votes[seat] < 0;
    }

    @Override
    public void bot(int seat, Random random) {
        JsonObject move = new JsonObject();

        if (this.phase == Phase.JUMP) {
            move.addProperty("keys", robotKeys(random, 1 + random.nextInt(3)));
        } else {
            move.addProperty("mark", robotMark(this.keys, random));
        }

        this.play(seat, move);
    }

    /**
     * A robot's dive: a run with A and D, the jump, then moves held for a while in the air. {@code skill} 0 (a
     * lazy dive, for a sleeper) to 3.
     */
    static String robotKeys(Random random, int skill) {
        StringBuilder keys = new StringBuilder("0");
        int run = 9 + random.nextInt(9);

        for (int step = 0; step < run; step++) {
            keys.append(skill > 0 && random.nextInt(8) == 0 ? '0' : step % 2 == 0 ? 'a' : 'd');
        }

        keys.append(' ');

        String[] moves = {"a", "d", "s", "w", "e", "z", "x"};
        int length = skill == 0 ? 0 : 3 + skill * 2 + random.nextInt(3);

        for (int index = 0; index < length; index++) {
            String key = moves[random.nextInt(moves.length)];
            int hold = key.equals("s")
                    ? 3
                    : key.equals("a") || key.equals("d") ? 4 + random.nextInt(5) : 2 + random.nextInt(3);

            keys.append(key.repeat(hold));

            if (random.nextInt(3) == 0) keys.append("0".repeat(1 + random.nextInt(2)));
        }

        keys.append("0".repeat(4));
        return keys.toString();
    }

    /** A robot's mark: the moves of the dive (how many, how varied, the somersaults), and a little mood. */
    static int robotMark(String keys, Random random) {
        int air = keys.indexOf(' ');

        if (air < 0) return 1 + random.nextInt(3);

        String flight = keys.substring(air + 1);
        int moves = 0;
        int somersaults = 0;
        Set<Character> kinds = new HashSet<>();
        char previous = '0';

        for (char key : flight.toCharArray()) {
            if (key != previous && key != '0' && key != ' ') {
                moves++;
                kinds.add(key);

                if (key == 's') somersaults++;
            }

            previous = key;
        }

        double mark = 2
                + Math.min(4, moves * 0.6)
                + kinds.size() * 0.4
                + Math.min(2, somersaults * 0.7)
                + random.nextGaussian() * 0.8;

        return (int) Math.max(0, Math.min(10, Math.round(mark)));
    }

    @Override
    public void forfeit(int seat) {
        if (seat >= this.seats || this.out[seat]) return;

        this.out[seat] = true;

        int left = 0;

        for (boolean gone : this.out) {
            if (!gone) left++;
        }

        if (left < 2) {
            this.end();
            return;
        }

        if (seat == this.jumper && this.phase == Phase.JUMP) this.next(this.clock);
    }

    @Override
    public boolean finished() {
        return this.finished;
    }

    @Override
    public List<Integer> winners() {
        return this.winners;
    }

    @Override
    public JsonObject view(int seat) {
        JsonObject view = new JsonObject();
        boolean shown = this.phase == Phase.SHOW || this.phase == Phase.VOTE || this.phase == Phase.SCORE;
        long length =
                switch (this.phase) {
                    case INTRO -> INTRO_MS;
                    case JUMP -> JUMP_MS;
                    case SHOW -> this.showMs();
                    case VOTE -> VOTE_MS;
                    case SCORE -> SCORE_MS;
                    default -> 0L;
                };

        view.addProperty("turn", this.phase == Phase.JUMP ? this.jumper : -1);
        view.addProperty("phase", this.phase.name().toLowerCase());
        view.addProperty("jumper", this.jumper);
        view.addProperty("round", this.round);
        view.addProperty("rounds", this.rounds);
        view.addProperty(
                "timeLeft", this.phaseStart < 0 ? length : Math.max(0L, this.phaseStart + length - this.clock));
        view.addProperty("elapsed", this.phaseStart < 0 ? 0L : Math.max(0L, this.clock - this.phaseStart));
        view.addProperty("keys", shown ? this.keys : "");
        view.addProperty("average", this.phase == Phase.SCORE || this.finished ? this.average : -1);

        JsonArray players = new JsonArray();

        for (int other = 0; other < this.seats; other++) {
            JsonObject entry = new JsonObject();

            entry.addProperty("total", Math.round(this.totals[other] * 10) / 10.0);
            entry.addProperty("last", this.last[other]);
            entry.addProperty("dives", this.dives[other]);
            entry.addProperty("voted", this.votes[other] >= 0);
            entry.addProperty("mark", this.phase == Phase.SCORE || other == seat ? this.votes[other] : -1);
            entry.addProperty("out", this.out[other]);
            players.add(entry);
        }

        view.add("players", players);
        return view;
    }

    double total(int seat) {
        return this.totals[seat];
    }

    String phaseName() {
        return this.phase.name();
    }
}
