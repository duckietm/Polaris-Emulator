package polaris.hotel.games.wobble;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import polaris.hotel.games.GameTexts;

/**
 * One Wobble Squabble duel: two fighters on a beam over the pool, each trying to stay balanced while knocking the other
 * into the water. The beam has {@link #BEAM} places; the left fighter faces right, the right one faces left.
 *
 * <p>Each fighter's balance goes from -100 (falling to the left) to 100 (to the right), and drifts: a tilt grows by
 * itself, the wind adds noise, and the fighter leans against it (Q / E, held). A push (A / D) hits the fighter next to
 * you on that side; a push in the air makes you lunge. Moving (N / M) wobbles a little. Stabilising (space), once a
 * round as in Habbo's original game, sets you straight again. At 100 you fall in: the other wins the round. After
 * {@link #SUDDEN_MS} of a round, sudden death: everything wobbles more.
 *
 * <p>The time goes by in steps of {@link #STEP_MS}, run when somebody asks ({@link #advance}), so the rules run the
 * same in the tests. Not thread-safe: the manager locks the duel.
 */
public final class WobbleMatch {

    public enum State {
        WAITING,
        COUNTDOWN,
        PLAYING,
        ROUND_OVER,
        FINISHED
    }

    public static final int BEAM = 8;
    public static final long STEP_MS = 50L;
    static final long COUNTDOWN_MS = 3000L;
    static final long ROUND_OVER_MS = 3500L;
    static final long SUDDEN_MS = 45_000L;
    static final long PUSH_COOLDOWN_MS = 800L;
    static final long MOVE_COOLDOWN_MS = 350L;
    /** How long the fighter shows they are setting themselves straight (the animation). */
    static final long STABILISE_MS = 600L;

    static final double FALL = 100.0;

    private static final double GRAVITY = 0.003;
    private static final double NOISE = 0.25;
    private static final double DAMPING = 0.93;
    private static final double LEAN = 0.7;
    private static final double MAX_SPEED = 6.0;
    private static final double HIT = 9.0;
    private static final double RECOIL = 1.5;
    private static final double LUNGE = 3.5;
    private static final double MOVE_WOBBLE = 1.5;
    private static final long MAX_CATCH_UP_MS = 10_000L;
    private static final int LOG_SIZE = 40;

    private static final String[] BOT_NAMES = {"Buoy", "Splash", "Mimosa", "Gaston", "Lili", "Barney"};
    private static final String[] BOT_FIGURES = {
        "hr-828-45.hd-180-1.ch-3215-92.lg-3023-110.sh-305-62",
        "hr-515-33.hd-600-1.ch-635-73.lg-716-82.sh-735-68",
        "hr-893-48.hd-180-2.ch-3030-81.lg-275-82.sh-290-91",
        "hr-890-45.hd-600-1.ch-665-92.lg-720-82.sh-907-62",
        "hr-170-42.hd-190-8.ch-215-66.lg-280-110.sh-300-64",
        "hr-545-33.hd-600-10.ch-635-66.lg-696-82.sh-725-64"
    };
    private static final String[] BOT_GENDERS = {"M", "F", "F", "M", "M", "F"};
    /** For a robot of level 1, 2, 3: how often it thinks, how far it lets itself tilt, how badly it judges. */
    private static final long[] BOT_REACTION_MS = {0, 420, 260, 140};

    private static final double[] BOT_THRESHOLD = {0, 32, 22, 14};
    private static final double[] BOT_ERROR = {0, 14, 7, 3};

    /** A fighter on the beam; a robot has a negative id. */
    public static final class Fighter {
        final int userId;
        final String name;
        final String figure;
        final String gender;
        final boolean bot;
        final long joined;
        int pos;
        double balance;
        double speed;
        int lean;
        /** A hit's impulse, added after the speed limit (a hit may go beyond it). */
        double impulse;

        boolean fell;
        int wins;
        int roundsLost;
        long pushReadyAt;
        long moveReadyAt;
        long stabiliseUntil;
        /** Stabilise is used once a round. */
        boolean stabilised;
        /** The last thing the fighter did or underwent (push, miss, hit, move, stabilise), for the animation. */
        String action = "";

        int actionDir;
        long actionAt;
        long lastSeen;
        long nextThinkAt;

        Fighter(int userId, String name, String figure, String gender, boolean bot, long joined, long now) {
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

        public String name() {
            return this.name;
        }

        public boolean bot() {
            return this.bot;
        }

        public int pos() {
            return this.pos;
        }

        public double balance() {
            return this.balance;
        }

        public boolean fell() {
            return this.fell;
        }

        public int wins() {
            return this.wins;
        }

        public long lastSeen() {
            return this.lastSeen;
        }

        boolean stabilising(long now) {
            return now < this.stabiliseUntil;
        }

        private void did(String what, int dir, long now) {
            this.action = what;
            this.actionDir = dir;
            this.actionAt = now;
        }
    }

    /** Something that happened, for the narrator: its number, its kind and its words. */
    public record Event(long seq, String type, String a, String b) {}

    private final int id;
    private final Random random;
    private final Fighter[] sides = new Fighter[2];
    private final Deque<Event> log = new ArrayDeque<>();
    private WobbleRules rules;
    private State state = State.WAITING;
    private int round;
    private long phaseEndsAt;
    private long roundStartedAt;
    private long clock;
    private long seq;
    private long joins;
    private int nextBotId = -1;
    private int winnerSide = -1;
    private boolean sudden;
    private boolean recorded;

    public WobbleMatch(int id, WobbleRules rules, Random random) {
        this.id = id;
        this.rules = rules;
        this.random = random;
    }

    // ---- The two sides

    public void join(int userId, String name, String figure, String gender, long now) {
        if (this.side(userId) >= 0) return;
        if (this.state != State.WAITING && this.state != State.FINISHED) throw new WobbleException("already_started");

        int free = this.sides[0] == null ? 0 : this.sides[1] == null ? 1 : -1;

        if (free < 0) throw new WobbleException("table_full");

        this.sides[free] = new Fighter(userId, name, figure, gender, false, ++this.joins, now);
        this.event("join", name, "");
    }

    public void addBot(int hostId, long now) {
        this.requireHost(hostId);
        if (!this.rules.bots()) throw new WobbleException("no_bots");
        if (this.state != State.WAITING && this.state != State.FINISHED) throw new WobbleException("already_started");

        int free = this.sides[0] == null ? 0 : this.sides[1] == null ? 1 : -1;

        if (free < 0) throw new WobbleException("table_full");

        String[] names = GameTexts.botNames("wobble", BOT_NAMES);
        int index = this.random.nextInt(BOT_FIGURES.length);
        String name = names[this.random.nextInt(names.length)];

        this.sides[free] = new Fighter(
                this.nextBotId--, name, BOT_FIGURES[index], BOT_GENDERS[index], true, ++this.joins, now);
        this.event("join", name, "");
    }

    public void removeBot(int hostId) {
        this.requireHost(hostId);
        if (this.state != State.WAITING && this.state != State.FINISHED) throw new WobbleException("already_started");

        for (int side = 0; side < 2; side++) {
            if (this.sides[side] != null && this.sides[side].bot) {
                this.event("leave", this.sides[side].name, "");
                this.sides[side] = null;
                return;
            }
        }

        throw new WobbleException("no_bot");
    }

    public void setRules(int hostId, WobbleRules next) {
        this.requireHost(hostId);
        if (this.state != State.WAITING && this.state != State.FINISHED) throw new WobbleException("already_started");

        this.rules = next;

        if (!next.bots()) {
            for (int side = 0; side < 2; side++) {
                if (this.sides[side] != null && this.sides[side].bot) this.sides[side] = null;
            }
        }

        this.event("rules", "", "");
    }

    /** A fighter leaves: during a match, the other wins it. */
    public void leave(int userId, long now) {
        int side = this.side(userId);

        if (side < 0) return;

        Fighter gone = this.sides[side];
        boolean playing =
                this.state == State.COUNTDOWN || this.state == State.PLAYING || this.state == State.ROUND_OVER;

        this.sides[side] = null;
        this.event("leave", gone.name, "");

        if (playing) {
            Fighter other = this.sides[1 - side];

            if (other != null) {
                this.winnerSide = 1 - side;
                this.state = State.FINISHED;
                this.event("forfeit", gone.name, other.name);
            } else {
                this.state = State.WAITING;
            }
        }
    }

    public void seen(int userId, long now) {
        int side = this.side(userId);

        if (side >= 0) this.sides[side].lastSeen = now;
    }

    // ---- The match

    public void start(int hostId, long now) {
        this.requireHost(hostId);
        if (this.state != State.WAITING && this.state != State.FINISHED) throw new WobbleException("already_started");
        if (this.sides[0] == null || this.sides[1] == null) throw new WobbleException("no_opponent");

        for (Fighter fighter : this.sides) {
            fighter.wins = 0;
            fighter.roundsLost = 0;
        }

        this.round = 0;
        this.winnerSide = -1;
        this.recorded = false;
        this.log.clear();
        this.event("start", this.sides[0].name, this.sides[1].name);
        this.beginRound(now);
    }

    private void beginRound(long now) {
        this.round++;
        this.sudden = false;
        this.clock = now;

        int[] places = {2, BEAM - 3};

        for (int side = 0; side < 2; side++) {
            Fighter fighter = this.sides[side];

            fighter.pos = places[side];
            fighter.balance = (this.random.nextDouble() - 0.5) * 6;
            fighter.speed = 0;
            fighter.impulse = 0;
            fighter.lean = 0;
            fighter.fell = false;
            fighter.pushReadyAt = 0;
            fighter.moveReadyAt = 0;
            fighter.stabiliseUntil = 0;
            fighter.stabilised = false;
            fighter.action = "";
            fighter.nextThinkAt = now + COUNTDOWN_MS + 300;
        }

        this.state = State.COUNTDOWN;
        this.phaseEndsAt = now + COUNTDOWN_MS;
        this.event("round", String.valueOf(this.round), "");
    }

    /** Leans against the wobble while the key is held: -1 to the left, 1 to the right, 0 to stop. */
    public void lean(int userId, int dir, long now) {
        this.advance(now);

        Fighter fighter = this.fighter(userId);

        fighter.lean = Integer.signum(dir);
    }

    /** Pushes the fighter next to you so they tilt to that side (-1 left, 1 right); in the air, you lunge. */
    public void push(int userId, int dir, long now) {
        this.advance(now);

        Fighter fighter = this.active(userId);
        int way = dir < 0 ? -1 : 1;

        if (now < fighter.pushReadyAt) throw new WobbleException("too_soon");

        Fighter other = this.sides[1 - this.side(userId)];

        fighter.pushReadyAt = now + PUSH_COOLDOWN_MS;

        if (Math.abs(other.pos - fighter.pos) == 1 && !other.fell) {
            double strength = HIT * (0.85 + this.random.nextDouble() * 0.35);

            other.impulse += way * strength;
            other.did("hit", way, now);
            fighter.impulse -= way * RECOIL;
            fighter.did("push", way, now);
        } else {
            fighter.impulse += way * LUNGE;
            fighter.did("miss", way, now);
        }
    }

    /** Takes a step along the beam: -1 to the left, 1 to the right; never through the other fighter. */
    public void move(int userId, int dir, long now) {
        this.advance(now);

        Fighter fighter = this.active(userId);
        int side = this.side(userId);
        Fighter other = this.sides[1 - side];
        int to = fighter.pos + (dir < 0 ? -1 : 1);

        if (now < fighter.moveReadyAt) throw new WobbleException("too_soon");
        if (to < 0 || to >= BEAM) throw new WobbleException("beam_end");
        if (side == 0 ? to >= other.pos : to <= other.pos) throw new WobbleException("blocked");

        fighter.pos = to;
        fighter.moveReadyAt = now + MOVE_COOLDOWN_MS;
        fighter.impulse += this.random.nextGaussian() * MOVE_WOBBLE;
        fighter.did("move", dir < 0 ? -1 : 1, now);
    }

    /** Sets the fighter straight again: balance back to the middle, the wobble stopped. Once a round. */
    public void stabilise(int userId, long now) {
        this.advance(now);

        Fighter fighter = this.active(userId);

        if (fighter.stabilised) throw new WobbleException("already_stabilised");

        fighter.stabilised = true;
        fighter.balance = 0;
        fighter.speed = 0;
        fighter.impulse = 0;
        fighter.stabiliseUntil = now + STABILISE_MS;
        fighter.did("stabilise", 0, now);
    }

    // ---- The clock

    /** Runs the duel up to now, step by step. */
    public void advance(long now) {
        if (this.state == State.WAITING || this.state == State.FINISHED) {
            this.clock = now;
            return;
        }

        if (now - this.clock > MAX_CATCH_UP_MS) this.clock = now - MAX_CATCH_UP_MS;

        while (this.clock + STEP_MS <= now) {
            long time = this.clock + STEP_MS;

            this.clock = time;

            switch (this.state) {
                case COUNTDOWN -> {
                    if (time >= this.phaseEndsAt) {
                        this.state = State.PLAYING;
                        this.roundStartedAt = time;
                        this.event("go", "", "");
                    }
                }
                case PLAYING -> this.step(time);
                case ROUND_OVER -> {
                    if (time >= this.phaseEndsAt) this.afterRound(time);
                }
                default -> {
                    return;
                }
            }

            if (this.state == State.WAITING || this.state == State.FINISHED) return;
        }
    }

    private void step(long time) {
        if (!this.sudden && time - this.roundStartedAt >= SUDDEN_MS) {
            this.sudden = true;
            this.event("sudden", "", "");
        }

        double gravity = GRAVITY * (this.sudden ? 2.0 : 1.0);
        double noise = NOISE * (this.sudden ? 2.5 : 1.0);

        for (int side = 0; side < 2; side++) {
            Fighter fighter = this.sides[side];

            if (fighter.bot) this.botThinks(fighter, this.sides[1 - side], time);
        }

        for (int side = 0; side < 2 && this.state == State.PLAYING; side++) {
            Fighter fighter = this.sides[side];

            fighter.speed += fighter.balance * gravity + this.random.nextGaussian() * noise + fighter.lean * LEAN;
            fighter.speed *= DAMPING;
            fighter.speed = Math.max(-MAX_SPEED, Math.min(MAX_SPEED, fighter.speed));
            fighter.speed += fighter.impulse;
            fighter.impulse = 0;
            fighter.balance += fighter.speed;

            if (Math.abs(fighter.balance) >= FALL) this.fall(side, time);
        }
    }

    private void fall(int side, long time) {
        Fighter loser = this.sides[side];
        Fighter winner = this.sides[1 - side];

        loser.fell = true;
        loser.balance = Math.signum(loser.balance) * FALL;
        loser.roundsLost++;
        loser.did("fall", (int) Math.signum(loser.balance), time);
        winner.wins++;
        this.state = State.ROUND_OVER;
        this.phaseEndsAt = time + ROUND_OVER_MS;
        this.event("fall", loser.name, winner.name);
    }

    private void afterRound(long time) {
        for (int side = 0; side < 2; side++) {
            if (this.sides[side].wins >= this.rules.roundsToWin()) {
                this.winnerSide = side;
                this.state = State.FINISHED;
                this.event("win", this.sides[side].name, "");
                return;
            }
        }

        this.beginRound(time);
    }

    // ---- Robots

    /**
     * A robot thinks now and then (the better it plays, the more often): it leans against the tilt it sees coming,
     * crouches when it tilts too far, walks up to the other, and pushes them the way they already lean.
     */
    private void botThinks(Fighter bot, Fighter other, long time) {
        if (time < bot.nextThinkAt) return;

        int level = this.rules.botLevel();
        long reaction = BOT_REACTION_MS[level];

        bot.nextThinkAt = time + reaction + this.random.nextInt((int) (reaction / 2) + 1);

        double coming = bot.balance + bot.speed * 5 + this.random.nextGaussian() * BOT_ERROR[level];
        double threshold = BOT_THRESHOLD[level];

        bot.lean = coming > threshold ? -1 : coming < -threshold ? 1 : 0;

        try {
            if (Math.abs(bot.balance) > 55 + 10 * (3 - level)
                    && !bot.stabilised
                    && this.random.nextInt(100) < 35 + 20 * level) {
                this.stabilise(bot.userId, time);
                return;
            }

            int gap = Math.abs(other.pos - bot.pos);

            if (gap > 1 && time >= bot.moveReadyAt && Math.abs(bot.balance) < 45 && this.random.nextInt(100) < 45) {
                this.move(bot.userId, other.pos > bot.pos ? 1 : -1, time);
                return;
            }

            if (gap == 1
                    && time >= bot.pushReadyAt
                    && Math.abs(bot.balance) < 50
                    && this.random.nextInt(100) < 25 + 15 * level) {
                double aim = other.balance + other.speed * 3;
                boolean smart = this.random.nextInt(100) < 25 + 25 * level && Math.abs(aim) > 5;

                this.push(bot.userId, smart ? (aim < 0 ? -1 : 1) : (this.random.nextBoolean() ? -1 : 1), time);
            }
        } catch (WobbleException e) {
            // Too soon, blocked: the robot thinks again later.
        }
    }

    // ---- Helpers

    private void event(String type, String a, String b) {
        this.log.addLast(new Event(++this.seq, type, a, b));

        while (this.log.size() > LOG_SIZE) this.log.pollFirst();
    }

    private Fighter fighter(int userId) {
        int side = this.side(userId);

        if (side < 0) throw new WobbleException("no_table");

        return this.sides[side];
    }

    /** The fighter of this player, while the round is on and they are still on the beam. */
    private Fighter active(int userId) {
        Fighter fighter = this.fighter(userId);

        if (this.state != State.PLAYING || fighter.fell) throw new WobbleException("not_playing");

        return fighter;
    }

    private void requireHost(int userId) {
        if (this.hostId() != userId) throw new WobbleException("not_host");
    }

    public int side(int userId) {
        for (int side = 0; side < 2; side++) {
            if (this.sides[side] != null && this.sides[side].userId == userId) return side;
        }

        return -1;
    }

    /** The host: the player who came first among those still here. */
    public int hostId() {
        Fighter host = null;

        for (Fighter fighter : this.sides) {
            if (fighter != null && !fighter.bot && (host == null || fighter.joined < host.joined)) host = fighter;
        }

        return host == null ? 0 : host.userId;
    }

    public boolean hasPlayers() {
        return this.hostId() != 0;
    }

    public List<Fighter> fighters() {
        List<Fighter> list = new ArrayList<>();

        for (Fighter fighter : this.sides) {
            if (fighter != null) list.add(fighter);
        }

        return list;
    }

    public Fighter onSide(int side) {
        return this.sides[side];
    }

    public int id() {
        return this.id;
    }

    public WobbleRules rules() {
        return this.rules;
    }

    public State state() {
        return this.state;
    }

    public int round() {
        return this.round;
    }

    public long phaseEndsAt() {
        return this.phaseEndsAt;
    }

    public long roundStartedAt() {
        return this.roundStartedAt;
    }

    public boolean sudden() {
        return this.sudden;
    }

    public int winnerSide() {
        return this.winnerSide;
    }

    public List<Event> log() {
        return new ArrayList<>(this.log);
    }

    /** Whether the finished match still has to be written to the scores (true once). */
    public boolean takeRecord() {
        if (this.state != State.FINISHED || this.recorded || this.winnerSide < 0) return false;

        this.recorded = true;
        return true;
    }
}
