package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Copy Writer, from Lost Monkey (Habbo's iPhone game): the parrot shows a row of letters on the five coins (A, B,
 * X, Y, Z), one after the other; everybody then types it back. Each round the row grows by one; a mistake, or the
 * time running out, sends the monkey to sleep. The last one awake wins; if the last ones fall asleep in the same
 * round, they share the win.
 */
public final class CopyWriter implements HallGame {

    static final String LETTERS = "ABXYZ";
    static final int FIRST_LENGTH = 3;
    static final int MAX_LENGTH = 30;
    static final long INTRO_MS = 3000L;
    static final long RESULT_MS = 2500L;

    private enum Phase {
        INTRO,
        SHOW,
        ANSWER,
        RESULT,
        END
    }

    private int seats;
    private final StringBuilder row = new StringBuilder();
    private Phase phase = Phase.INTRO;
    private long phaseStart = -1;
    private long clock;
    private boolean[] awake = new boolean[0];
    private String[] answers = new String[0];
    private int[] reached = new int[0];
    private boolean finished;
    private List<Integer> winners = List.of();
    private Random random;

    @Override
    public void start(int seats, Random random) {
        this.seats = seats;
        this.random = random;
        this.row.setLength(0);

        for (int index = 0; index < FIRST_LENGTH - 1; index++) this.row.append(this.letter(random));

        this.phase = Phase.INTRO;
        this.phaseStart = -1;
        this.awake = new boolean[seats];
        this.answers = new String[seats];
        this.reached = new int[seats];

        for (int seat = 0; seat < seats; seat++) this.awake[seat] = true;

        this.finished = false;
        this.winners = List.of();
    }

    private char letter(Random random) {
        return LETTERS.charAt(random.nextInt(LETTERS.length()));
    }

    /** The parrot's pace: a letter every 700 ms at first, down to 380 ms. */
    long stepMs() {
        return Math.max(380L, 760L - (this.row.length() - FIRST_LENGTH) * 40L);
    }

    long showMs() {
        return 900L + this.row.length() * this.stepMs();
    }

    long answerMs() {
        return 4000L + this.row.length() * 900L;
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
                if (elapsed >= INTRO_MS) this.nextRound(this.phaseStart + INTRO_MS);
            }
            case SHOW -> {
                if (elapsed >= this.showMs()) this.enter(Phase.ANSWER, this.phaseStart + this.showMs());
            }
            case ANSWER -> {
                if (elapsed >= this.answerMs() || this.everybodyAnswered())
                    this.judge(Math.min(now, this.phaseStart + this.answerMs()));
            }
            case RESULT -> {
                if (elapsed >= RESULT_MS) this.nextRound(this.phaseStart + RESULT_MS);
            }
            default -> {}
        }
    }

    private void enter(Phase next, long at) {
        this.phase = next;
        this.phaseStart = at;
    }

    private void nextRound(long at) {
        if (this.row.length() >= MAX_LENGTH) {
            this.end(this.awakeSeats());
            return;
        }

        this.row.append(this.letter(this.random));

        for (int seat = 0; seat < this.seats; seat++) this.answers[seat] = null;

        this.enter(Phase.SHOW, at);
    }

    private boolean everybodyAnswered() {
        for (int seat = 0; seat < this.seats; seat++) {
            if (this.awake[seat] && this.answers[seat] == null) return false;
        }

        return true;
    }

    private List<Integer> awakeSeats() {
        List<Integer> list = new ArrayList<>();

        for (int seat = 0; seat < this.seats; seat++) {
            if (this.awake[seat]) list.add(seat);
        }

        return list;
    }

    private void judge(long at) {
        List<Integer> before = this.awakeSeats();
        String expected = this.row.toString();

        for (int seat : before) {
            if (expected.equals(this.answers[seat])) {
                this.reached[seat] = expected.length();
            } else {
                this.awake[seat] = false;
            }
        }

        List<Integer> after = this.awakeSeats();

        // One left (of several), or nobody: the end. Alone from the start, one plays until they fall asleep.
        if (after.isEmpty()) {
            this.end(before);
        } else if (after.size() == 1 && this.seats > 1) {
            this.end(after);
        } else {
            this.enter(Phase.RESULT, at);
        }
    }

    private void end(List<Integer> top) {
        this.winners = List.copyOf(top);
        this.phase = Phase.END;
        this.finished = true;
    }

    @Override
    public void play(int seat, JsonObject move) {
        if (!this.waitingFor(seat)) throw new HallException("not_your_turn");

        String typed = move != null && move.has("keys")
                ? move.get("keys").getAsString().toUpperCase()
                : "";

        if (typed.isEmpty()
                || typed.length() > this.row.length()
                || !typed.chars().allMatch(c -> LETTERS.indexOf(c) >= 0)) throw new HallException("bad_move");

        this.answers[seat] = typed;
    }

    @Override
    public boolean waitingFor(int seat) {
        return !this.finished
                && this.phase == Phase.ANSWER
                && seat < this.seats
                && this.awake[seat]
                && this.answers[seat] == null;
    }

    /** A robot's memory fails more and more as the row grows. */
    @Override
    public void bot(int seat, Random random) {
        String expected = this.row.toString();
        double chance = Math.max(0.15, 1.02 - 0.06 * expected.length());
        String typed = expected;

        if (random.nextDouble() > chance) {
            int wrong = random.nextInt(expected.length());
            char other = expected.charAt(wrong) == 'A' ? 'B' : 'A';

            typed = expected.substring(0, wrong) + other;
        }

        JsonObject move = new JsonObject();

        move.addProperty("keys", typed);
        this.play(seat, move);
    }

    @Override
    public void forfeit(int seat) {
        if (seat >= this.seats || this.finished) return;

        this.awake[seat] = false;

        List<Integer> left = this.awakeSeats();

        if (left.size() <= 1 && this.seats > 1) this.end(left);
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
        long length =
                switch (this.phase) {
                    case INTRO -> INTRO_MS;
                    case SHOW -> this.showMs();
                    case ANSWER -> this.answerMs();
                    case RESULT -> RESULT_MS;
                    default -> 0L;
                };
        boolean shown = this.phase != Phase.INTRO && this.phase != Phase.ANSWER || this.finished;

        view.addProperty("turn", -1);
        view.addProperty("phase", this.phase.name().toLowerCase());
        view.addProperty("length", this.phase == Phase.INTRO ? 0 : this.row.length());
        // The row is sent while the parrot shows it (the window plays it), and after: not while one types it.
        view.addProperty("row", shown ? this.row.toString() : "");
        view.addProperty("stepMs", this.stepMs());
        view.addProperty(
                "timeLeft", this.phaseStart < 0 ? length : Math.max(0L, this.phaseStart + length - this.clock));
        view.addProperty("elapsed", this.phaseStart < 0 ? 0L : Math.max(0L, this.clock - this.phaseStart));

        JsonArray players = new JsonArray();

        for (int other = 0; other < this.seats; other++) {
            JsonObject entry = new JsonObject();

            entry.addProperty("awake", this.awake[other]);
            entry.addProperty("answered", this.answers[other] != null);
            entry.addProperty("reached", this.reached[other]);
            players.add(entry);
        }

        view.add("players", players);
        return view;
    }

    String row() {
        return this.row.toString();
    }

    String phaseName() {
        return this.phase.name();
    }
}
