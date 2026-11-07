package polaris.hotel.games.fastfood;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * One game of Fast Food, as Habbo's original game (BaseJump, 2010) plays it: each player has a table, two diners
 * waiting, and a plate hanging up high. Drop the plate, open its parachute as late as you dare: too late, it lands too
 * fast and the food is lost; too early, it floats down slowly. A plate served safely is a star; the first to fill their
 * stars wins. Dropping before the start is a false start: the plate falls empty. Power-ups, won now and then with a
 * plate served: the missile (it bursts the parachute of the player in the lead), the shield (it stops a missile) and
 * the big parachute (it brakes ten times harder, opened at the last moment).
 *
 * <p>A plate's height goes from 1 (up high) to 0 (the table), its speed in heights a second; it moves in steps of
 * {@link #STEP_MS} as in the original (speed += acceleration, a parachute's speed never under its terminal speed,
 * height -= speed). The parachute is opened at the moment the player saw (a player's action may arrive up to
 * {@link #LAG_MS} late): a landing is only decided that long after the plate touched the table.
 *
 * <p>The time goes by when somebody asks ({@link #advance}): the rules run the same in the tests. Not thread-safe: the
 * manager locks the game.
 */
public final class FastFoodMatch {

    public enum State {
        WAITING,
        COUNTDOWN,
        PLAYING,
        FINISHED
    }

    /** Where a plate is: hanging up high, falling, under its parachute, falling with a burst one, served, lost. */
    public enum Phase {
        HANGING,
        FALLING,
        CHUTE,
        BROKEN,
        LANDED,
        CRASHED
    }

    public static final int MISSILE = 1;
    public static final int SHIELD = 2;
    public static final int BIG_PARACHUTE = 3;

    static final long STEP_MS = 10L;
    static final long COUNTDOWN_MS = 4000L;
    static final long PLATE_TIMER_MS = 1600L;
    static final long FALSE_START_PENALTY_MS = 1500L;
    static final long LAG_MS = 350L;
    static final long MISSILE_MS = 900L;
    static final long GAME_MS = 300_000L;
    static final long RECENT_MS = 1500L;
    /** The fastest a plate may touch the table and stay whole, in heights a second. */
    public static final double SAFE_SPEED = 0.30;

    public static final double BIG_BRAKE = 10.0;
    public static final double BIG_TERMINAL = 0.08;
    static final double POWER_CHANCE = 0.4;
    private static final long MAX_CATCH_UP_MS = 10_000L;
    private static final int LOG_SIZE = 60;

    /** A food and how it falls: its gravity, its parachute's braking, its parachute's speed (heights a second). */
    public record Food(String name, double gravity, double brake, double terminal) {}

    /** The original's foods, a tray twice (FallingObjectType). */
    public static final List<Food> FOODS = List.of(
            new Food("soup", 0.55, 3.2, 0.12),
            new Food("pizza", 0.55, 3.4, 0.12),
            new Food("burger", 0.60, 3.2, 0.12),
            new Food("tray", 0.62, 3.0, 0.13),
            new Food("cake", 0.70, 2.8, 0.14),
            new Food("tray", 0.62, 3.0, 0.13));

    private static final String[] BOT_NAMES = {"Mamie Lasagne", "Chef Tartine", "Croquette", "Pistache", "Gaufrette"};
    private static final String[] BOT_FIGURES = {
        "hr-828-45.hd-180-1.ch-3215-92.lg-3023-110.sh-305-62",
        "hr-515-33.hd-600-1.ch-635-73.lg-716-82.sh-735-68",
        "hr-893-48.hd-180-2.ch-3030-81.lg-275-82.sh-290-91",
        "hr-890-45.hd-600-1.ch-665-92.lg-720-82.sh-907-62",
        "hr-170-42.hd-190-8.ch-215-66.lg-280-110.sh-300-64"
    };
    private static final String[] BOT_GENDERS = {"M", "F", "M", "F", "M"};

    /** A plate: its food, where it is, and the moments that decide its fall. */
    public static final class Plate {
        final int id;
        final int food;
        Phase phase = Phase.HANGING;
        long readyAt;
        boolean falseStart;
        long releasedAt = -1;
        long chuteAt = -1;
        long brokenAt = -1;
        boolean big;
        boolean shield;
        long endedAt = -1;
        // The fall simulated so far (reset when the moments change).
        long simAt = -1;
        double y = 1.0;
        double v;
        long impactAt = -1;
        double impactSpeed;

        Plate(int id, int food, long readyAt) {
            this.id = id;
            this.food = food;
            this.readyAt = readyAt;
        }

        public int id() {
            return this.id;
        }

        public int food() {
            return this.food;
        }

        public Phase phase() {
            return this.phase;
        }

        public double y() {
            return this.y;
        }

        public double v() {
            return this.v;
        }

        public boolean big() {
            return this.big;
        }

        public boolean shield() {
            return this.shield;
        }

        public boolean falseStart() {
            return this.falseStart;
        }

        public long readyAt() {
            return this.readyAt;
        }

        public long endedAt() {
            return this.endedAt;
        }

        boolean inAir() {
            return this.phase == Phase.FALLING || this.phase == Phase.CHUTE || this.phase == Phase.BROKEN;
        }

        boolean ended() {
            return this.phase == Phase.LANDED || this.phase == Phase.CRASHED;
        }

        void resetFall() {
            this.simAt = -1;
            this.y = 1.0;
            this.v = 0;
            this.impactAt = -1;
        }
    }

    /** A player at their table; a robot has a negative id. */
    public static final class Player {
        final int userId;
        final String name;
        final String figure;
        final String gender;
        final boolean bot;
        final long joined;
        int seat;
        int stars;
        long lastStarAt;
        int held;
        Plate plate;
        long lastSeen;
        // A robot's plans for its plate.
        long botDropAt = -1;
        double botMargin;
        long botPowerAt = -1;

        Player(int userId, String name, String figure, String gender, boolean bot, int seat, long joined, long now) {
            this.userId = userId;
            this.name = name;
            this.figure = figure;
            this.gender = gender;
            this.bot = bot;
            this.seat = seat;
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

        public int seat() {
            return this.seat;
        }

        public int stars() {
            return this.stars;
        }

        public int held() {
            return this.held;
        }

        public Plate plate() {
            return this.plate;
        }

        public long lastSeen() {
            return this.lastSeen;
        }
    }

    /** A missile on its way to a plate. */
    record Missile(int from, int to, int plateId, long hitAt) {}

    /** Something that happened, for the client's effects and sounds. */
    public record Event(long seq, long at, String type, int userId, int a, int b, int c) {}

    private final int id;
    private final Random random;
    private final List<Player> players = new ArrayList<>();
    private final List<Missile> missiles = new ArrayList<>();
    private final Deque<Event> log = new ArrayDeque<>();
    private FastFoodRules rules;
    private State state = State.WAITING;
    private long phaseEndsAt;
    private long clock;
    private long seq;
    private long joins;
    private int nextPlateId = 1;
    private int nextBotId = -1;
    private List<Integer> winners = List.of();
    private boolean recorded;

    public FastFoodMatch(int id, FastFoodRules rules, Random random) {
        this.id = id;
        this.rules = rules;
        this.random = random;
    }

    // ---- The tables

    public void join(int userId, String name, String figure, String gender, long now) {
        if (this.player(userId) != null) return;
        if (this.state == State.COUNTDOWN || this.state == State.PLAYING)
            throw new FastFoodException("already_started");
        if (this.players.size() >= this.rules.tables()) throw new FastFoodException("table_full");

        this.players.add(new Player(userId, name, figure, gender, false, this.freeSeat(), ++this.joins, now));
        this.event("join", userId, 0, 0, 0, now);
    }

    public void addBot(int hostId, long now) {
        this.requireHost(hostId);
        if (!this.rules.bots()) throw new FastFoodException("no_bots");
        if (this.state == State.COUNTDOWN || this.state == State.PLAYING)
            throw new FastFoodException("already_started");
        if (this.players.size() >= this.rules.tables()) throw new FastFoodException("table_full");

        String name = null;

        for (String candidate : BOT_NAMES) {
            if (this.byName(candidate) == null) {
                name = candidate;
                break;
            }
        }

        if (name == null) throw new FastFoodException("table_full");

        int look = this.random.nextInt(BOT_FIGURES.length);

        this.players.add(new Player(
                this.nextBotId--,
                name,
                BOT_FIGURES[look],
                BOT_GENDERS[look],
                true,
                this.freeSeat(),
                ++this.joins,
                now));
        this.event("join", this.nextBotId + 1, 0, 0, 0, now);
    }

    public void removeBot(int hostId) {
        this.requireHost(hostId);
        if (this.state == State.COUNTDOWN || this.state == State.PLAYING)
            throw new FastFoodException("already_started");

        for (int index = this.players.size() - 1; index >= 0; index--) {
            if (this.players.get(index).bot) {
                this.players.remove(index);
                return;
            }
        }

        throw new FastFoodException("no_bot");
    }

    public void setRules(int hostId, FastFoodRules next) {
        this.requireHost(hostId);
        if (this.state == State.COUNTDOWN || this.state == State.PLAYING)
            throw new FastFoodException("already_started");

        this.rules = next;

        if (!next.bots()) this.players.removeIf(player -> player.bot);

        // Fewer tables: the last robots go, then the last to come.
        while (this.players.size() > next.tables()) {
            Player gone = null;

            for (Player player : this.players) {
                if (player.bot) gone = player;
            }

            this.players.remove(gone != null ? gone : this.players.get(this.players.size() - 1));
        }

        for (Player player : this.players) {
            if (player.seat >= next.tables()) player.seat = -1;
        }

        for (Player player : this.players) {
            if (player.seat < 0) player.seat = this.freeSeat();
        }

        this.state = State.WAITING;
    }

    public void leave(int userId, long now) {
        Player player = this.player(userId);

        if (player == null) return;

        this.players.remove(player);
        this.event("leave", userId, player.seat, 0, 0, now);

        // Alone with robots or nobody: the game goes on for whoever stays; one left plays to the end.
        if (this.state == State.PLAYING && this.players.isEmpty()) this.finish(now);
    }

    public void seen(int userId, long now) {
        Player player = this.player(userId);

        if (player != null) player.lastSeen = now;
    }

    public void start(int hostId, long now) {
        this.requireHost(hostId);
        if (this.state == State.COUNTDOWN || this.state == State.PLAYING)
            throw new FastFoodException("already_started");
        if (this.players.size() < 2) throw new FastFoodException("not_enough_players");

        this.log.clear();
        this.missiles.clear();
        this.winners = List.of();
        this.recorded = false;
        this.clock = now;

        for (Player player : this.players) {
            player.stars = 0;
            player.lastStarAt = 0;
            player.held = 0;
            player.botPowerAt = -1;
            this.newPlate(player, now + COUNTDOWN_MS);
        }

        this.state = State.COUNTDOWN;
        this.phaseEndsAt = now + COUNTDOWN_MS;
        this.event("countdown", 0, 0, 0, 0, now);
    }

    // ---- What a player does

    /** Drops the plate (before the start: a false start, the plate falls empty). */
    public void drop(int userId, long now) {
        Player player = this.playing(userId);
        Plate plate = player.plate;

        if (plate == null || plate.phase != Phase.HANGING) throw new FastFoodException("no_plate");

        if (this.state == State.COUNTDOWN) {
            plate.falseStart = true;
        } else if (plate.readyAt > now) {
            throw new FastFoodException("not_ready");
        }

        this.release(player, plate, now);
    }

    /**
     * Opens the parachute when the player saw it open: {@code elapsedMs} after the plate was dropped, as their screen
     * counted, taken if it is at most {@link #LAG_MS} before now.
     */
    public void chute(int userId, long elapsedMs, long now) {
        Player player = this.playing(userId);
        Plate plate = player.plate;

        if (plate == null || plate.phase != Phase.FALLING || plate.falseStart) throw new FastFoodException("no_plate");

        long elapsed = now - plate.releasedAt;
        long claimed = elapsedMs < 0 ? elapsed : Math.max(Math.max(0, elapsed - LAG_MS), Math.min(elapsed, elapsedMs));
        long at = plate.releasedAt + (claimed / STEP_MS) * STEP_MS;

        if (plate.brokenAt >= 0 && plate.brokenAt <= at) throw new FastFoodException("chute_broken");

        // Already on the table at that moment: too late.
        fly(plate, at);
        if (plate.impactAt >= 0 && plate.impactAt <= at) throw new FastFoodException("too_late");

        plate.chuteAt = at;
        plate.resetFall();
        fly(plate, now);
        plate.phase = plate.brokenAt >= 0 && plate.brokenAt <= now ? Phase.BROKEN : Phase.CHUTE;
        this.event("chute", userId, plate.id, plate.big ? 1 : 0, 0, now);
    }

    /** Uses the power-up held. */
    public void usePower(int userId, long now) {
        Player player = this.playing(userId);

        if (this.state != State.PLAYING) throw new FastFoodException("not_started");
        if (player.held == 0) throw new FastFoodException("no_power");

        Plate plate = player.plate;

        switch (player.held) {
            case MISSILE -> {
                Player target = this.missileTarget(player);

                if (target == null) throw new FastFoodException("no_target");

                this.missiles.add(new Missile(player.userId, target.userId, target.plate.id, now + MISSILE_MS));
                this.event("missile", player.userId, target.userId, target.plate.id, (int) MISSILE_MS, now);
            }
            case SHIELD -> {
                if (plate == null || plate.ended() || plate.shield) throw new FastFoodException("no_plate");

                plate.shield = true;
            }
            case BIG_PARACHUTE -> {
                if (plate == null || !(plate.phase == Phase.HANGING || plate.phase == Phase.FALLING) || plate.big)
                    throw new FastFoodException("no_plate");

                plate.big = true;
            }
            default -> throw new FastFoodException("no_power");
        }

        this.event("power", player.userId, player.held, plate == null ? 0 : plate.id, 0, now);
        player.held = 0;
    }

    /** The missile goes for the player in the lead whose plate is in the air. */
    private Player missileTarget(Player from) {
        Player best = null;

        for (Player player : this.players) {
            if (player == from || player.plate == null || !player.plate.inAir()) continue;
            if (player.plate.impactAt >= 0) continue;
            if (best == null
                    || player.stars > best.stars
                    || (player.stars == best.stars && player.plate.y < best.plate.y)) best = player;
        }

        return best;
    }

    // ---- The time

    public void advance(long now) {
        if (this.state == State.WAITING || this.state == State.FINISHED) {
            this.clock = now;
            return;
        }

        if (now - this.clock > MAX_CATCH_UP_MS) this.clock = now - MAX_CATCH_UP_MS;

        while (this.clock + STEP_MS <= now) {
            long time = this.clock + STEP_MS;

            this.clock = time;

            if (this.state == State.COUNTDOWN) {
                this.stepPlates(time);

                if (time >= this.phaseEndsAt) {
                    this.state = State.PLAYING;
                    this.phaseEndsAt = time + GAME_MS;
                    this.event("go", 0, 0, 0, 0, time);
                }

                continue;
            }

            if (this.state != State.PLAYING) return;

            this.stepMissiles(time);
            this.stepPlates(time);
            this.stepBots(time);

            if (this.state != State.PLAYING) return;

            if (time >= this.phaseEndsAt) {
                this.finish(time);
                return;
            }
        }
    }

    private void stepPlates(long time) {
        for (Player player : new ArrayList<>(this.players)) {
            Plate plate = player.plate;

            if (plate == null) continue;

            if (plate.ended()) {
                if (this.state == State.PLAYING && time >= plate.endedAt + this.timerFor(plate)) {
                    this.newPlate(player, time);
                }

                continue;
            }

            if (!plate.inAir()) continue;

            fly(plate, time);

            if (plate.brokenAt >= 0 && plate.brokenAt <= time) plate.phase = Phase.BROKEN;
            else if (plate.chuteAt >= 0 && plate.chuteAt <= time) plate.phase = Phase.CHUTE;

            if (plate.impactAt >= 0 && time >= plate.impactAt + LAG_MS) this.decide(player, plate, time);
        }
    }

    private long timerFor(Plate plate) {
        return plate.falseStart ? PLATE_TIMER_MS + FALSE_START_PENALTY_MS : PLATE_TIMER_MS;
    }

    private void decide(Player player, Plate plate, long time) {
        plate.endedAt = time;
        plate.y = 0;

        if (!plate.falseStart && plate.impactSpeed <= SAFE_SPEED + 1e-9) {
            plate.phase = Phase.LANDED;
            plate.v = 0;
            player.stars++;
            player.lastStarAt = time;
            this.event("land", player.userId, plate.id, player.stars, plate.food, time);

            if (this.rules.powers() && player.held == 0 && this.random.nextDouble() < POWER_CHANCE) {
                player.held = 1 + this.random.nextInt(3);
                player.botPowerAt = -1;
                this.event("powerup", player.userId, player.held, 0, 0, time);
            }

            if (player.stars >= this.rules.plates()) {
                this.winners = List.of(player.userId);
                this.finish(time);
            }
        } else {
            plate.phase = Phase.CRASHED;
            plate.v = 0;
            this.event(plate.falseStart ? "falsestart" : "crash", player.userId, plate.id, plate.food, 0, time);
        }
    }

    private void stepMissiles(long time) {
        for (Missile missile : new ArrayList<>(this.missiles)) {
            if (missile.hitAt() > time) continue;

            this.missiles.remove(missile);

            Player target = this.player(missile.to());
            Plate plate = target == null ? null : target.plate;

            if (plate == null || plate.id != missile.plateId() || !plate.inAir() || plate.brokenAt >= 0) continue;

            fly(plate, time);
            if (plate.impactAt >= 0 && plate.impactAt <= time) continue;

            if (plate.shield) {
                plate.shield = false;
                this.event("hit", missile.to(), plate.id, 1, missile.from(), time);
            } else {
                plate.brokenAt = time;
                plate.phase = Phase.BROKEN;
                this.event("hit", missile.to(), plate.id, 0, missile.from(), time);
            }
        }
    }

    private void newPlate(Player player, long readyAt) {
        player.plate = new Plate(this.nextPlateId++, this.random.nextInt(FOODS.size()), readyAt);

        if (player.bot) {
            player.botDropAt = readyAt + 250 + this.random.nextInt(650);
            // How much higher than needed the robot opens: mostly safe, now and then too late.
            player.botMargin = this.random.nextDouble() < 0.1
                    ? -0.04 + this.random.nextDouble() * 0.04
                    : 0.02 + this.random.nextDouble() * 0.2;
        }
    }

    private void release(Player player, Plate plate, long now) {
        plate.releasedAt = now;
        plate.phase = Phase.FALLING;
        plate.resetFall();
        this.event("drop", player.userId, plate.id, plate.food, plate.falseStart ? 1 : 0, now);
    }

    // ---- The robots

    private void stepBots(long time) {
        for (Player player : this.players) {
            if (!player.bot || player.plate == null) continue;

            Plate plate = player.plate;

            if (plate.phase == Phase.HANGING && time >= plate.readyAt && time >= player.botDropAt) {
                if (player.held == BIG_PARACHUTE || player.held == SHIELD) this.usePower(player.userId, time);
                this.release(player, plate, time);
                continue;
            }

            if (plate.phase == Phase.FALLING && plate.impactAt < 0) {
                double brake = FOODS.get(plate.food).brake() * (plate.big ? BIG_BRAKE : 1.0);
                double need = Math.max(0, (plate.v * plate.v - SAFE_SPEED * SAFE_SPEED) / (2 * brake));

                if (plate.y <= need + player.botMargin) this.chute(player.userId, time - plate.releasedAt, time);
            }

            if (player.held == MISSILE) {
                if (player.botPowerAt < 0) player.botPowerAt = time + 500 + this.random.nextInt(3000);

                if (time >= player.botPowerAt && this.missileTarget(player) != null) this.usePower(player.userId, time);
            }
        }
    }

    // ---- The fall

    /** Runs a plate's fall up to {@code until} (from where it was simulated, or from its drop). */
    static void fly(Plate plate, long until) {
        if (plate.releasedAt < 0) return;

        if (plate.simAt < 0) {
            plate.simAt = plate.releasedAt;
            plate.y = 1.0;
            plate.v = 0;
            plate.impactAt = -1;
        }

        Food food = FOODS.get(plate.food);
        double dt = STEP_MS / 1000.0;

        while (plate.impactAt < 0 && plate.simAt + STEP_MS <= until) {
            long t = plate.simAt;
            boolean chute = plate.chuteAt >= 0 && plate.chuteAt <= t && (plate.brokenAt < 0 || t < plate.brokenAt);

            if (chute) {
                plate.v -= food.brake() * (plate.big ? BIG_BRAKE : 1.0) * dt;
                plate.v = Math.max(plate.v, plate.big ? BIG_TERMINAL : food.terminal());
            } else {
                plate.v += food.gravity() * dt;
            }

            plate.y -= plate.v * dt;
            plate.simAt = t + STEP_MS;

            if (plate.y <= 0) {
                plate.y = 0;
                plate.impactAt = plate.simAt;
                plate.impactSpeed = plate.v;
            }
        }
    }

    // ---- The end

    private void finish(long time) {
        this.state = State.FINISHED;
        this.missiles.clear();

        if (this.winners.isEmpty() && !this.players.isEmpty()) {
            int best = 0;

            for (Player player : this.players) best = Math.max(best, player.stars);

            List<Integer> top = new ArrayList<>();

            if (best > 0) {
                for (Player player : this.players) {
                    if (player.stars == best) top.add(player.userId);
                }
            }

            this.winners = top;
        }

        this.event("end", this.winners.isEmpty() ? 0 : this.winners.get(0), 0, 0, 0, time);
    }

    public boolean takeRecord() {
        if (this.state != State.FINISHED || this.recorded) return false;

        this.recorded = true;
        return true;
    }

    // ---- Helpers

    private int freeSeat() {
        for (int seat = 0; seat < this.rules.tables(); seat++) {
            final int wanted = seat;

            if (this.players.stream().noneMatch(player -> player.seat == wanted)) return seat;
        }

        throw new FastFoodException("table_full");
    }

    private Player playing(int userId) {
        Player player = this.player(userId);

        if (player == null) throw new FastFoodException("no_table");
        if (this.state != State.COUNTDOWN && this.state != State.PLAYING) throw new FastFoodException("not_started");

        return player;
    }

    private void requireHost(int userId) {
        if (this.hostId() != userId) throw new FastFoodException("not_host");
    }

    private Player byName(String name) {
        for (Player player : this.players) {
            if (player.name.equals(name)) return player;
        }

        return null;
    }

    private void event(String type, int userId, int a, int b, int c, long at) {
        this.log.addLast(new Event(++this.seq, at, type, userId, a, b, c));

        while (this.log.size() > LOG_SIZE) this.log.removeFirst();
    }

    public Player player(int userId) {
        for (Player player : this.players) {
            if (player.userId == userId) return player;
        }

        return null;
    }

    /** The host: the first player who came and is still here (robots never host). */
    public int hostId() {
        Player host = null;

        for (Player player : this.players) {
            if (!player.bot && (host == null || player.joined < host.joined)) host = player;
        }

        return host == null ? 0 : host.userId;
    }

    public boolean hasPlayers() {
        return this.hostId() != 0;
    }

    public int id() {
        return this.id;
    }

    public FastFoodRules rules() {
        return this.rules;
    }

    public State state() {
        return this.state;
    }

    public long phaseEndsAt() {
        return this.phaseEndsAt;
    }

    public List<Player> players() {
        return Collections.unmodifiableList(this.players);
    }

    public List<Integer> winners() {
        return this.winners;
    }

    public List<Event> log() {
        return List.copyOf(this.log);
    }

    List<Missile> missiles() {
        return Collections.unmodifiableList(this.missiles);
    }

    /** Missiles in the air: who fired, at whom, landing in how long. */
    public List<long[]> missilesInAir(long now) {
        List<long[]> list = new ArrayList<>();

        for (Missile missile : this.missiles) {
            list.add(new long[] {missile.from(), missile.to(), Math.max(0, missile.hitAt() - now)});
        }

        return list;
    }
}
