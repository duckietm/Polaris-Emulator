package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The hotel quiz: {@link #ROUNDS} questions drawn from the bank, four answers each, everybody answering at the same
 * time within {@link #ANSWER_MS}. A right answer is worth 500 points and up to 500 more for answering fast; then the
 * answer is shown for a moment and the next question comes. The most points win (a tie shares the win).
 */
public final class Quiz implements HallGame {

    static final int ROUNDS = 10;
    static final long INTRO_MS = 3000L;
    static final long ANSWER_MS = 15_000L;
    static final long REVEAL_MS = 4000L;

    /** A question of the bank: its category, the question, its four answers and the right one (0 to 3). */
    public record Question(String category, String text, List<String> answers, int right) {}

    private final List<Question> bank;
    private final List<Question> questions = new ArrayList<>();
    private int seats;
    private int round = -1;
    private boolean revealing;
    private long phaseStart = -1;
    private long clock;
    private int[] scores = new int[0];
    private int[] answers = new int[0];
    private long[] answeredAt = new long[0];
    private int[] gained = new int[0];
    private boolean finished;
    private List<Integer> winners = List.of();

    public Quiz(List<Question> bank) {
        this.bank = bank;
    }

    @Override
    public void start(int seats, Random random) {
        if (this.bank.size() < ROUNDS) throw new HallException("no_questions");

        List<Question> pool = new ArrayList<>(this.bank);

        Collections.shuffle(pool, random);
        this.questions.clear();

        for (Question question : pool.subList(0, ROUNDS)) {
            // The answers are shuffled too: the right one is not always in the same place.
            List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));

            Collections.shuffle(order, random);

            List<String> shuffled = new ArrayList<>();

            for (int index : order) shuffled.add(question.answers().get(index));

            this.questions.add(
                    new Question(question.category(), question.text(), shuffled, order.indexOf(question.right())));
        }

        this.seats = seats;
        this.scores = new int[seats];
        this.answers = new int[seats];
        this.answeredAt = new long[seats];
        this.gained = new int[seats];
        this.round = -1;
        this.revealing = false;
        this.phaseStart = -1;
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

        if (this.round < 0) {
            if (now - this.phaseStart >= INTRO_MS) this.ask(this.phaseStart + INTRO_MS);
            return;
        }

        if (!this.revealing) {
            boolean everybody = true;

            for (int seat = 0; seat < this.seats; seat++) {
                if (this.answers[seat] < 0) everybody = false;
            }

            if (everybody || now - this.phaseStart >= ANSWER_MS)
                this.reveal(Math.min(now, this.phaseStart + ANSWER_MS));
            return;
        }

        if (now - this.phaseStart >= REVEAL_MS) {
            if (this.round + 1 >= ROUNDS) this.end();
            else this.ask(this.phaseStart + REVEAL_MS);
        }
    }

    private void ask(long at) {
        this.round++;
        this.revealing = false;
        this.phaseStart = at;
        Arrays.fill(this.answers, -1);
        Arrays.fill(this.gained, 0);
    }

    private void reveal(long at) {
        Question question = this.questions.get(this.round);

        for (int seat = 0; seat < this.seats; seat++) {
            if (this.answers[seat] == question.right()) {
                long taken = Math.max(0, this.answeredAt[seat] - this.phaseStart);

                this.gained[seat] = 500 + (int) Math.round(500.0 * Math.max(0, ANSWER_MS - taken) / ANSWER_MS);
                this.scores[seat] += this.gained[seat];
            }
        }

        this.revealing = true;
        this.phaseStart = at;
    }

    private void end() {
        int best = Arrays.stream(this.scores).max().orElse(0);
        List<Integer> top = new ArrayList<>();

        for (int seat = 0; seat < this.seats; seat++) {
            if (this.scores[seat] == best && best > 0) top.add(seat);
        }

        this.winners = top;
        this.finished = true;
    }

    @Override
    public void play(int seat, JsonObject move) {
        if (!this.waitingFor(seat)) throw new HallException("not_your_turn");

        int answer = TurnGame.intOf(move, "answer");

        if (answer < 0 || answer > 3) throw new HallException("bad_move");

        this.answers[seat] = answer;
        this.answeredAt[seat] = this.clock;
    }

    @Override
    public boolean waitingFor(int seat) {
        return !this.finished && this.round >= 0 && !this.revealing && seat < this.seats && this.answers[seat] < 0;
    }

    /** A robot answers right three times in five (otherwise at random). */
    @Override
    public void bot(int seat, Random random) {
        Question question = this.questions.get(this.round);
        JsonObject move = new JsonObject();

        move.addProperty("answer", random.nextInt(5) < 3 ? question.right() : random.nextInt(4));
        this.play(seat, move);
    }

    @Override
    public void forfeit(int seat) {
        if (seat < this.seats) this.scores[seat] = Integer.MIN_VALUE / 2;

        if (seat < this.seats && this.answers[seat] < 0) {
            this.answers[seat] = 4;
        }
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

        view.addProperty("turn", -1);
        view.addProperty("round", this.round);
        view.addProperty("rounds", ROUNDS);
        view.addProperty("phase", this.finished ? "end" : this.round < 0 ? "intro" : this.revealing ? "reveal" : "ask");
        view.addProperty(
                "timeLeft",
                this.phaseStart < 0
                        ? INTRO_MS
                        : Math.max(
                                0L,
                                this.phaseStart
                                        + (this.round < 0 ? INTRO_MS : this.revealing ? REVEAL_MS : ANSWER_MS)
                                        - this.clock));

        if (this.round >= 0) {
            Question question = this.questions.get(this.round);
            JsonArray answers = new JsonArray();

            for (String answer : question.answers()) answers.add(answer);

            view.addProperty("category", question.category());
            view.addProperty("question", question.text());
            view.add("answers", answers);
            view.addProperty("right", this.revealing || this.finished ? question.right() : -1);
        }

        JsonArray players = new JsonArray();

        for (int other = 0; other < this.seats; other++) {
            JsonObject entry = new JsonObject();

            entry.addProperty("score", Math.max(0, this.scores[other]));
            entry.addProperty("answered", this.answers[other] >= 0);
            entry.addProperty("answer", this.revealing || other == seat ? this.answers[other] : -1);
            entry.addProperty("gained", this.revealing ? this.gained[other] : 0);
            players.add(entry);
        }

        view.add("players", players);
        return view;
    }

    int score(int seat) {
        return this.scores[seat];
    }
}
