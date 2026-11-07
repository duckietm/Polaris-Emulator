package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The snail race: a snail a seat (2 to 6), a track of {@link #LENGTH}. After a countdown the snails crawl on their own,
 * each at a speed that changes every second or so; now and then one dozes off for a while or gets a burst of energy.
 * Their players cheer them on (a short boost, once in a while). The first across the line wins; the race is over when
 * every snail has arrived (or after {@link #MAX_MS}).
 */
public final class SnailRace implements HallGame {

    public static final double LENGTH = 100;
    static final long COUNTDOWN_MS = 3500L;
    static final long STEP_MS = 100L;
    static final long CHEER_COOLDOWN_MS = 3000L;
    static final long CHEER_MS = 1200L;
    static final long MAX_MS = 90_000L;
    static final double BASE_MIN = 2.2;
    static final double BASE_MAX = 4.6;
    static final double CHEER_BONUS = 2.4;

    /** What a snail is doing now. */
    enum Mood {
        CRAWL,
        SLEEP,
        TURBO,
        CHEERED
    }

    static final class Snail {
        double x;
        double speed;
        Mood mood = Mood.CRAWL;
        long moodUntil;
        long changeAt;
        long cheeredUntil;
        long cheerReadyAt;
        int place;
        long finishedAt = -1;
        boolean out;
    }

    private final List<Snail> snails = new ArrayList<>();
    private Random random;
    private long startAt = -1;
    private long clock = -1;
    private int arrived;
    private boolean finished;
    private List<Integer> winners = List.of();

    @Override
    public void start(int seats, Random random) {
        this.random = random;
        this.snails.clear();

        for (int seat = 0; seat < seats; seat++) this.snails.add(new Snail());

        this.startAt = -1;
        this.clock = -1;
        this.arrived = 0;
        this.finished = false;
        this.winners = List.of();
    }

    private boolean running(long now) {
        return this.startAt >= 0 && now >= this.startAt && !this.finished;
    }

    @Override
    public void tick(long now, Random random) {
        if (this.startAt < 0) {
            this.startAt = now + COUNTDOWN_MS;
            this.clock = now;

            for (Snail snail : this.snails) {
                snail.cheerReadyAt = this.startAt;
                snail.changeAt = this.startAt;
            }
        }

        while (!this.finished && this.clock + STEP_MS <= now) {
            this.clock += STEP_MS;

            if (this.clock > this.startAt) this.step(this.clock);
        }
    }

    private void step(long time) {
        double dt = STEP_MS / 1000.0;

        for (Snail snail : this.snails) {
            if (snail.finishedAt >= 0 || snail.out) continue;

            if (snail.mood != Mood.CRAWL && time >= snail.moodUntil) snail.mood = Mood.CRAWL;
            if (snail.mood == Mood.CHEERED && time >= snail.cheeredUntil) snail.mood = Mood.CRAWL;

            if (time >= snail.changeAt) {
                snail.speed = BASE_MIN + this.random.nextDouble() * (BASE_MAX - BASE_MIN);
                snail.changeAt = time + 700 + this.random.nextInt(900);

                int roll = this.random.nextInt(100);

                if (snail.mood == Mood.CRAWL && roll < 6) {
                    snail.mood = Mood.SLEEP;
                    snail.moodUntil = time + 1500 + this.random.nextInt(1500);
                } else if (snail.mood == Mood.CRAWL && roll < 12) {
                    snail.mood = Mood.TURBO;
                    snail.moodUntil = time + 1200 + this.random.nextInt(800);
                }
            }

            snail.x = Math.min(LENGTH, snail.x + this.speedOf(snail, time) * dt);

            if (snail.x >= LENGTH) {
                snail.finishedAt = time;
                snail.place = ++this.arrived;

                if (snail.place == 1) this.winners = List.of(this.snails.indexOf(snail));
            }
        }

        boolean allIn = this.snails.stream().allMatch(snail -> snail.finishedAt >= 0 || snail.out);

        if (allIn || time - this.startAt >= MAX_MS) this.finished = true;
    }

    double speedOf(Snail snail, long time) {
        double speed =
                switch (snail.mood) {
                    case SLEEP -> 0;
                    case TURBO -> snail.speed * 2.2;
                    default -> snail.speed;
                };

        if (time < snail.cheeredUntil && snail.mood != Mood.SLEEP) speed += CHEER_BONUS;

        return speed;
    }

    @Override
    public void play(int seat, JsonObject move) {
        if (this.finished) throw new HallException("finished");

        this.cheer(seat, this.clock);
    }

    private void cheer(int seat, long time) {
        Snail snail = this.snails.get(seat);

        if (!this.running(time) || snail.finishedAt >= 0 || time < snail.cheerReadyAt)
            throw new HallException("not_your_turn");

        snail.cheeredUntil = time + CHEER_MS;
        snail.cheerReadyAt = time + CHEER_COOLDOWN_MS;

        // Cheering wakes a sleeping snail up.
        if (snail.mood == Mood.SLEEP) snail.mood = Mood.CRAWL;
    }

    /** A seat may cheer when the race is on, its snail still running and its cheer ready. */
    @Override
    public boolean waitingFor(int seat) {
        if (this.finished || this.startAt < 0 || this.clock < this.startAt || seat >= this.snails.size()) return false;

        Snail snail = this.snails.get(seat);

        return snail.finishedAt < 0 && !snail.out && this.clock >= snail.cheerReadyAt;
    }

    /** A robot cheers its snail (the table makes it think a moment first). */
    @Override
    public void bot(int seat, Random random) {
        this.cheer(seat, this.clock);
    }

    @Override
    public void forfeit(int seat) {
        if (this.finished || seat >= this.snails.size()) return;

        this.snails.get(seat).out = true;

        if (this.snails.stream().allMatch(snail -> snail.finishedAt >= 0 || snail.out)) this.finished = true;
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
        long now = this.clock;

        view.addProperty("turn", -1);
        view.addProperty("length", LENGTH);
        view.addProperty("startsIn", this.startAt < 0 ? COUNTDOWN_MS : Math.max(0L, this.startAt - now));
        view.addProperty("elapsed", this.startAt < 0 ? 0L : Math.max(0L, now - this.startAt));

        JsonArray list = new JsonArray();

        for (int index = 0; index < this.snails.size(); index++) {
            Snail snail = this.snails.get(index);
            JsonObject entry = new JsonObject();

            entry.addProperty("x", snail.x);
            entry.addProperty(
                    "speed", snail.finishedAt >= 0 || snail.out || now < this.startAt ? 0 : this.speedOf(snail, now));
            entry.addProperty(
                    "mood",
                    snail.finishedAt >= 0
                            ? "done"
                            : snail.out
                                    ? "out"
                                    : now < snail.cheeredUntil && snail.mood != Mood.SLEEP
                                            ? "cheered"
                                            : snail.mood.name().toLowerCase());
            entry.addProperty("place", snail.place);
            entry.addProperty("time", snail.finishedAt < 0 ? -1L : snail.finishedAt - this.startAt);
            entry.addProperty("cheerIn", Math.max(0L, snail.cheerReadyAt - now));
            list.add(entry);
        }

        view.add("snails", list);
        return view;
    }

    Snail snail(int seat) {
        return this.snails.get(seat);
    }
}
