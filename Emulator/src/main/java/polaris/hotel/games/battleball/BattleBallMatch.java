package polaris.hotel.games.battleball;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import polaris.hotel.games.GameTexts;

/**
 * One game of BattleBall: Rebound!, as Habbo's original game plays it (rules read from Kepler's BattleBall and the
 * Shockwave client's scripts). Teams bounce on balls across an arena; every game tile a player lands on takes their
 * colour, a step at a time (touched, clicked, pressed, sealed: three hits from grey, four in Barebones Classic); a tile
 * of another colour that is not sealed is stolen. Sealing a tile closes the areas it encloses: they are sealed too.
 * Points: 2, 6, 10 and 14 for the four steps, 2 more for a stolen tile; a team's score is what its tiles are worth.
 * Power-ups appear on the floor (light bulb, spring, flashlight, cannon, box of pins, harlequin, bomb, vacuum cleaner,
 * question mark), are picked up by bouncing on them and used with one button.
 *
 * <p>The time goes by in steps of {@link #STEP_MS}, run when somebody asks ({@link #advance}): the rules run the same in
 * the tests. Not thread-safe: the manager locks the game.
 */
public final class BattleBallMatch {

    public enum State {
        WAITING,
        COUNTDOWN,
        PLAYING,
        FINISHED
    }

    /** What a player is doing (the original's player states). */
    public enum PlayerState {
        NORMAL,
        STUNNED,
        HIGH_JUMPS,
        CLEANING_TILES,
        COLOURING_FOR_OPPONENT,
        FLYING,
        BALL_BROKEN
    }

    public static final int LIGHT_BULB = 1;
    public static final int SPRING = 2;
    public static final int FLASHLIGHT = 3;
    public static final int CANNON = 4;
    public static final int BOX_OF_PINS = 5;
    public static final int HARLEQUIN = 6;
    public static final int BOMB = 7;
    public static final int VACUUM = 8;
    public static final int QUESTION_MARK = 9;

    public static final int DEFAULT = 0;
    public static final int TOUCHED = 1;
    public static final int CLICKED = 2;
    public static final int PRESSED = 3;
    public static final int SEALED = 4;

    public static final long STEP_MS = 100L;
    static final long MOVE_MS = 450L;
    static final long COUNTDOWN_MS = 5000L;
    static final long STUN_MS = 5000L;
    static final long POWER_MS = 10_000L;
    static final long HELD_MS = 15_000L;
    static final long FLYING_MS = 800L;
    static final int MAX_FLOOR_POWERS = 2;
    private static final long MAX_CATCH_UP_MS = 10_000L;
    private static final int LOG_SIZE = 60;
    /** The eight directions, as Habbo turns: 0 up the y axis, 2 along x, 4 down y, 6 back along x. */
    private static final int[][] DIRECTIONS = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

    private static final String[] BOT_NAMES = {
        "Bondi",
        "Bounce",
        "Zebby",
        "Balthazar",
        "Nugget",
        "Ricochet",
        "Zoom",
        "Boing",
        "Ruby",
        "Comet",
        "Hop",
        "Pogo"
    };
    private static final String[] BOT_FIGURES = {
        "hr-828-45.hd-180-1.ch-3215-92.lg-3023-110.sh-305-62",
        "hr-515-33.hd-600-1.ch-635-73.lg-716-82.sh-735-68",
        "hr-893-48.hd-180-2.ch-3030-81.lg-275-82.sh-290-91",
        "hr-890-45.hd-600-1.ch-665-92.lg-720-82.sh-907-62",
        "hr-170-42.hd-190-8.ch-215-66.lg-280-110.sh-300-64",
        "hr-545-33.hd-600-10.ch-635-66.lg-696-82.sh-725-64"
    };
    private static final String[] BOT_GENDERS = {"M", "F", "M", "F", "M", "F"};

    /** A player on a ball; a robot has a negative id. */
    public static final class Player {
        final int userId;
        final String name;
        final String figure;
        final String gender;
        final boolean bot;
        final long joined;
        int team;
        int x;
        int y;
        int dir;
        int goalX = -1;
        int goalY = -1;
        long nextMoveAt;
        long movedAt;
        PlayerState state = PlayerState.NORMAL;
        long stateUntil;
        Player harlequinBy;
        int held;
        long heldUntil;
        long lastSeen;
        long thinkAt;

        Player(int userId, String name, String figure, String gender, boolean bot, int team, long joined, long now) {
            this.userId = userId;
            this.name = name;
            this.figure = figure;
            this.gender = gender;
            this.bot = bot;
            this.team = team;
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

        public int team() {
            return this.team;
        }

        public int x() {
            return this.x;
        }

        public int y() {
            return this.y;
        }

        public PlayerState state() {
            return this.state;
        }

        public int held() {
            return this.held;
        }

        public long lastSeen() {
            return this.lastSeen;
        }

        boolean canAct() {
            return this.state != PlayerState.STUNNED
                    && this.state != PlayerState.BALL_BROKEN
                    && this.state != PlayerState.FLYING;
        }

        /** The team whose colour this player paints now (a harlequin's, while it lasts). */
        int paintsFor() {
            return this.state == PlayerState.COLOURING_FOR_OPPONENT && this.harlequinBy != null
                    ? this.harlequinBy.team
                    : this.team;
        }

        /** Who gets the points of what this player paints. */
        int paintsForUser() {
            return this.state == PlayerState.COLOURING_FOR_OPPONENT && this.harlequinBy != null
                    ? this.harlequinBy.userId
                    : this.userId;
        }
    }

    /** What a tile is worth to a team, and to whom. */
    record Points(int team, int userId, int points) {}

    public record PowerUp(int id, int type, int x, int y, long despawnAt) {}

    public record Pin(int id, int x, int y, long until) {}

    /** Something that happened, for the client's effects and the narrator. */
    public record Event(long seq, long at, String type, int userId, int a, int b, int c, int d) {}

    private final int id;
    private final Random random;
    private final List<Player> players = new ArrayList<>();
    private final List<PowerUp> powerUps = new ArrayList<>();
    private final List<Pin> pins = new ArrayList<>();
    private final Deque<Event> log = new ArrayDeque<>();
    private BattleBallRules rules;
    private BattleBallArena arena;
    private int[][] colours;
    private int[][] states;
    private List<Points>[][] points;
    private State state = State.WAITING;
    private long phaseEndsAt;
    private long clock;
    private long secondAt;
    private long seq;
    private long joins;
    private int nextObjectId = 1;
    private int nextBotId = -1;
    private List<Integer> winners = List.of();
    private boolean recorded;

    public BattleBallMatch(int id, BattleBallRules rules, Random random) {
        this.id = id;
        this.rules = rules;
        this.random = random;
        this.arena = BattleBallArena.get(rules.arena());
        this.clearTiles();
    }

    // ---- The teams

    public void join(int userId, String name, String figure, String gender, int team, long now) {
        if (this.player(userId) != null) return;
        if (this.state == State.COUNTDOWN || this.state == State.PLAYING)
            throw new BattleBallException("already_started");
        if (this.players.size() >= this.rules.maxPlayers()) throw new BattleBallException("table_full");

        int chosen = this.freeTeam(team);
        Player player = new Player(userId, name, figure, gender, false, chosen, ++this.joins, now);

        this.players.add(player);
        this.event("join", userId, chosen, 0, 0, 0, now);
    }

    public void switchTeam(int userId, int team) {
        Player player = this.player(userId);

        if (player == null) throw new BattleBallException("no_table");
        if (this.state == State.COUNTDOWN || this.state == State.PLAYING)
            throw new BattleBallException("already_started");
        if (team < 0 || team >= this.rules.teams() || this.teamSize(team) >= this.rules.perTeam())
            throw new BattleBallException("team_full");

        player.team = team;
    }

    public void addBot(int hostId, int team, long now) {
        this.requireHost(hostId);
        if (!this.rules.bots()) throw new BattleBallException("no_bots");
        if (this.state == State.COUNTDOWN || this.state == State.PLAYING)
            throw new BattleBallException("already_started");
        if (this.players.size() >= this.rules.maxPlayers()) throw new BattleBallException("table_full");

        int chosen = this.freeTeam(team);
        String name = null;

        for (String candidate : GameTexts.botNames("battleball", BOT_NAMES)) {
            if (this.byName(candidate) == null) {
                name = candidate;
                break;
            }
        }

        if (name == null) throw new BattleBallException("table_full");

        int look = this.random.nextInt(BOT_FIGURES.length);

        this.players.add(new Player(
                this.nextBotId--, name, BOT_FIGURES[look], BOT_GENDERS[look], true, chosen, ++this.joins, now));
        this.event("join", this.nextBotId + 1, chosen, 0, 0, 0, now);
    }

    /** Fills every team up with robots. */
    public void fillWithBots(int hostId, long now) {
        this.requireHost(hostId);

        while (this.players.size() < this.rules.maxPlayers()) this.addBot(hostId, -1, now);
    }

    public void removeBot(int hostId) {
        this.requireHost(hostId);
        if (this.state == State.COUNTDOWN || this.state == State.PLAYING)
            throw new BattleBallException("already_started");

        for (int index = this.players.size() - 1; index >= 0; index--) {
            if (this.players.get(index).bot) {
                this.players.remove(index);
                return;
            }
        }

        throw new BattleBallException("no_bot");
    }

    public void setRules(int hostId, BattleBallRules next) {
        this.requireHost(hostId);
        if (this.state == State.COUNTDOWN || this.state == State.PLAYING)
            throw new BattleBallException("already_started");

        this.rules = next;
        this.arena = BattleBallArena.get(next.arena());
        this.clearTiles();

        if (!next.bots()) this.players.removeIf(player -> player.bot);

        // Whoever is in a team that went away, or a team grown too big, moves to a free one; the last robots go.
        for (Player player : new ArrayList<>(this.players)) {
            if (player.team >= next.teams() || this.teamSize(player.team) > next.perTeam()) {
                player.team = -1;

                try {
                    player.team = this.freeTeam(-1);
                } catch (BattleBallException e) {
                    this.players.remove(player);
                }
            }
        }

        this.state = State.WAITING;
    }

    public void leave(int userId, long now) {
        Player player = this.player(userId);

        if (player == null) return;

        this.players.remove(player);
        this.event("leave", userId, player.team, 0, 0, 0, now);
    }

    public void seen(int userId, long now) {
        Player player = this.player(userId);

        if (player != null) player.lastSeen = now;
    }

    // ---- The game

    public void start(int hostId, long now) {
        this.requireHost(hostId);
        if (this.state == State.COUNTDOWN || this.state == State.PLAYING)
            throw new BattleBallException("already_started");

        int teams = 0;
        int smallest = Integer.MAX_VALUE;
        int biggest = 0;

        for (int team = 0; team < this.rules.teams(); team++) {
            int size = this.teamSize(team);

            if (size == 0) continue;

            teams++;
            smallest = Math.min(smallest, size);
            biggest = Math.max(biggest, size);
        }

        if (teams < 2) throw new BattleBallException("not_enough_teams");
        if (biggest - smallest > 1) throw new BattleBallException("team_sizes");

        this.clearTiles();
        this.powerUps.clear();
        this.pins.clear();
        this.log.clear();
        this.winners = List.of();
        this.recorded = false;
        this.clock = now;
        this.secondAt = now + 1000L;

        for (int team = 0; team < 4; team++) {
            List<int[]> spawns = new ArrayList<>(this.arena.spawns(team));
            int index = 0;

            for (Player player : this.players) {
                if (player.team != team) continue;

                int[] spawn = spawns.isEmpty() ? new int[] {0, 0, 2} : spawns.get(index++ % spawns.size());

                player.x = spawn[0];
                player.y = spawn[1];
                player.dir = spawn[2];
                player.goalX = -1;
                player.goalY = -1;
                player.state = PlayerState.NORMAL;
                player.harlequinBy = null;
                player.held = 0;
                player.nextMoveAt = now + COUNTDOWN_MS;
                player.thinkAt = now + COUNTDOWN_MS + this.random.nextInt(600);
            }
        }

        this.state = State.COUNTDOWN;
        this.phaseEndsAt = now + COUNTDOWN_MS;
        this.event("countdown", 0, 0, 0, 0, 0, now);

        // A few power-ups wait on the floor from the start (0 to 2, as in the original).
        int first = this.random.nextInt(MAX_FLOOR_POWERS + 1);

        for (int index = 0; index < first; index++) this.spawnPowerUp(now);
    }

    /** Bounces towards a tile: the ball goes there a tile at a time. */
    public void walk(int userId, int x, int y, long now) {
        this.advance(now);

        Player player = this.active(userId);

        if (!this.arena.walkable(x, y)) throw new BattleBallException("bad_tile");

        player.goalX = x;
        player.goalY = y;
    }

    /** Uses the power-up the player holds. */
    public void usePower(int userId, long now) {
        this.advance(now);

        Player player = this.active(userId);

        if (player.held == 0) throw new BattleBallException("no_power");
        if (!player.canAct()) throw new BattleBallException("stunned");

        int power = player.held;

        player.held = 0;
        this.event("power", player.userId, power, player.x, player.y, player.dir, now);

        switch (power) {
            case LIGHT_BULB -> {
                for (int[] point : circle(player.x, player.y, 5)) this.colour(player, point[0], point[1]);
            }
            case SPRING -> this.setState(player, PlayerState.HIGH_JUMPS, now + POWER_MS);
            case FLASHLIGHT -> {
                for (int[] point : this.line(player.x, player.y, player.dir)) this.colour(player, point[0], point[1]);
            }
            case CANNON -> this.cannon(player, now);
            case BOX_OF_PINS -> this.boxOfPins(player, now);
            case HARLEQUIN -> {
                for (Player other : this.players) {
                    if (other.team == player.team || other.state != PlayerState.NORMAL) continue;

                    other.harlequinBy = player;
                    this.setState(other, PlayerState.COLOURING_FOR_OPPONENT, now + POWER_MS);
                }
            }
            case BOMB -> this.bomb(player, now);
            case VACUUM -> this.setState(player, PlayerState.CLEANING_TILES, now + POWER_MS);
            default -> {}
        }
    }

    // ---- The clock

    /** Runs the game up to now, step by step. */
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
                if (time >= this.phaseEndsAt) {
                    this.state = State.PLAYING;
                    this.phaseEndsAt = time + this.rules.minutes() * 60_000L;
                    this.event("go", 0, 0, 0, 0, 0, time);
                }

                continue;
            }

            if (this.state != State.PLAYING) return;

            this.step(time);

            if (time >= this.phaseEndsAt) {
                this.finish(time);
                return;
            }
        }
    }

    private void step(long time) {
        if (time >= this.secondAt) {
            this.secondAt += 1000L;
            this.everySecond(time);
        }

        for (Player player : this.players) {
            if (player.state != PlayerState.NORMAL && time >= player.stateUntil) this.stateOver(player, time);
            if (player.held != 0 && time >= player.heldUntil) player.held = 0;
        }

        for (Player player : this.players) {
            if (player.bot && time >= player.thinkAt) this.botThinks(player, time);
        }

        for (Player player : this.players) {
            if (time >= player.nextMoveAt) this.move(player, time);
        }
    }

    private void everySecond(long time) {
        this.powerUps.removeIf(power -> time >= power.despawnAt());
        this.pins.removeIf(pin -> time >= pin.until());

        // A new power-up now and then (6% a second, two at most on the floor).
        if (this.powerUps.size() < MAX_FLOOR_POWERS && this.random.nextInt(100) < 6) this.spawnPowerUp(time);
    }

    private void stateOver(Player player, long time) {
        if (player.state == PlayerState.FLYING) {
            this.stun(player, PlayerState.STUNNED, time);
            return;
        }

        player.state = PlayerState.NORMAL;
        player.harlequinBy = null;
    }

    private void finish(long time) {
        int[] scores = this.teamScores();
        int best = -1;
        List<Integer> top = new ArrayList<>();

        for (int team = 0; team < this.rules.teams(); team++) {
            if (this.teamSize(team) == 0) continue;
            if (scores[team] > best) {
                best = scores[team];
                top.clear();
            }

            if (scores[team] == best) top.add(team);
        }

        this.winners = List.copyOf(top);
        this.state = State.FINISHED;
        this.event("end", 0, top.isEmpty() ? -1 : top.get(0), top.size(), best, 0, time);
    }

    // ---- Moving

    private void move(Player player, long time) {
        if (!player.canAct() || player.goalX < 0) return;
        if (player.x == player.goalX && player.y == player.goalY) {
            player.goalX = -1;
            player.goalY = -1;
            return;
        }

        int[] next = this.nextStep(player);

        if (next == null) {
            // Nowhere to go now: the robot looks elsewhere, the player waits a moment.
            if (player.bot) player.thinkAt = time;

            player.nextMoveAt = time + MOVE_MS / 2;
            return;
        }

        player.dir = direction(player.x, player.y, next[0], next[1]);
        player.x = next[0];
        player.y = next[1];
        player.nextMoveAt = time + MOVE_MS;
        player.movedAt = time;
        this.land(player, time);
    }

    /** The first step on the shortest way to the goal, round the other players. */
    private int[] nextStep(Player player) {
        int width = this.arena.width();
        int height = this.arena.height();
        int[][] from = new int[width][height];
        Deque<int[]> queue = new ArrayDeque<>();
        Set<Integer> taken = new HashSet<>();

        for (Player other : this.players) {
            if (other != player) taken.add(other.x * 1000 + other.y);
        }

        for (int[] column : from) Arrays.fill(column, -1);

        from[player.x][player.y] = player.x * 1000 + player.y;
        queue.add(new int[] {player.x, player.y});

        boolean found = false;

        while (!queue.isEmpty() && !found) {
            int[] at = queue.poll();

            for (int[] delta : DIRECTIONS) {
                int nx = at[0] + delta[0];
                int ny = at[1] + delta[1];

                if (!this.stepAllowed(at[0], at[1], nx, ny) || from[nx][ny] != -1) continue;
                if (taken.contains(nx * 1000 + ny)) continue;

                from[nx][ny] = at[0] * 1000 + at[1];

                if (nx == player.goalX && ny == player.goalY) {
                    found = true;
                    break;
                }

                queue.add(new int[] {nx, ny});
            }
        }

        if (!found) return null;

        int x = player.goalX;
        int y = player.goalY;

        while (true) {
            int previous = from[x][y];
            int px = previous / 1000;
            int py = previous % 1000;

            if (px == player.x && py == player.y) return new int[] {x, y};

            x = px;
            y = py;
        }
    }

    private boolean stepAllowed(int x, int y, int nx, int ny) {
        if (!this.arena.walkable(nx, ny)) return false;
        if (Math.abs(this.arena.heightAt(nx, ny) - this.arena.heightAt(x, y)) > 1) return false;

        // A diagonal step does not cut a corner.
        if (nx != x && ny != y) return this.arena.walkable(nx, y) && this.arena.walkable(x, ny);

        return true;
    }

    /** The ball lands on a tile: a power-up is picked up, a pin bursts the ball, the tile takes the colour. */
    private void land(Player player, long time) {
        int x = player.x;
        int y = player.y;

        for (PowerUp power : new ArrayList<>(this.powerUps)) {
            if (power.x() != x || power.y() != y) continue;

            int type = power.type();

            if (type == QUESTION_MARK) {
                List<Integer> choices = new ArrayList<>(this.rules.powers());

                type = choices.isEmpty() ? LIGHT_BULB : choices.get(this.random.nextInt(choices.size()));
            }

            this.powerUps.remove(power);
            player.held = type;
            player.heldUntil = time + HELD_MS;
            this.event("pickup", player.userId, type, x, y, 0, time);
        }

        for (Pin pin : new ArrayList<>(this.pins)) {
            if (pin.x() == x && pin.y() == y) {
                this.pins.remove(pin);
                this.stun(player, PlayerState.BALL_BROKEN, time);
                return;
            }
        }

        if (!this.arena.gameTile(x, y)) return;

        if (player.state == PlayerState.HIGH_JUMPS) {
            if (this.states[x][y] != SEALED) {
                int team = player.paintsFor();

                this.score(x, y, SEALED, team, player.paintsForUser());
                this.colours[x][y] = team;
                this.states[x][y] = SEALED;
                this.fill(x, y, team, player.paintsForUser(), time);
            }

            return;
        }

        if (player.state == PlayerState.CLEANING_TILES) {
            this.clean(x, y);
            return;
        }

        this.touch(player, x, y, time);
    }

    /** A bounce on a tile: a step closer to sealed for its team, or stolen by another. */
    private void touch(Player player, int x, int y, long time) {
        if (this.states[x][y] == SEALED) return;

        int team = player.paintsFor();
        int user = player.paintsForUser();
        int next = this.colours[x][y] == team ? this.states[x][y] + 1 : this.rules.arena() == 5 ? TOUCHED : CLICKED;

        this.score(x, y, next, team, user);
        this.colours[x][y] = team;
        this.states[x][y] = next;

        if (next == SEALED) this.fill(x, y, team, user, time);
    }

    /** The light bulb's and the flashlight's colouring: grey counts as touched, another colour goes to clicked. */
    private void colour(Player player, int x, int y) {
        if (!this.arena.gameTile(x, y) || this.states[x][y] == SEALED) return;

        int team = player.paintsFor();
        int user = player.paintsForUser();
        int current = this.states[x][y] == DEFAULT ? TOUCHED : this.states[x][y];
        int next = this.colours[x][y] == team ? current + 1 : CLICKED;

        this.score(x, y, next, team, user);
        this.colours[x][y] = team;
        this.states[x][y] = next;

        if (next == SEALED) this.fill(x, y, team, user, this.clock);
    }

    /** The points of a tile's new step (as Kepler counts them); a tile taken from another team drops its points. */
    private void score(int x, int y, int next, int team, int user) {
        int earned =
                switch (next) {
                    case TOUCHED -> 2;
                    case CLICKED -> 6;
                    case PRESSED -> 10;
                    case SEALED -> 14;
                    default -> 0;
                };
        int colour = this.colours[x][y];

        if (colour != team && colour >= 0) earned += 2;
        if (colour != team) this.points[x][y].removeIf(points -> points.team() != team);
        if (earned > 0) this.points[x][y].add(new Points(team, user, earned));
    }

    /**
     * Sealing a tile seals the areas it closes: from each neighbour, the tiles that can be reached without crossing the
     * team's sealed tiles, as long as that never leads off the game's tiles. Each sealed tile is worth 14 points.
     */
    private void fill(int x, int y, int team, int user, long time) {
        int filled = 0;

        for (int[] delta : DIRECTIONS) {
            int nx = x + delta[0];
            int ny = y + delta[1];

            if (!this.arena.gameTile(nx, ny) || (this.states[nx][ny] == SEALED && this.colours[nx][ny] == team))
                continue;

            for (int[] tile : this.enclosed(nx, ny, team)) {
                if (this.states[tile[0]][tile[1]] == SEALED && this.colours[tile[0]][tile[1]] == team) continue;

                this.points[tile[0]][tile[1]].clear();
                this.points[tile[0]][tile[1]].add(new Points(team, user, 14));
                this.colours[tile[0]][tile[1]] = team;
                this.states[tile[0]][tile[1]] = SEALED;
                filled++;
            }
        }

        if (filled > 0) this.event("fill", user, team, filled, x, y, time);
    }

    private List<int[]> enclosed(int startX, int startY, int team) {
        List<int[]> area = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        Deque<int[]> stack = new ArrayDeque<>();

        stack.push(new int[] {startX, startY});
        seen.add(startX * 1000 + startY);

        while (!stack.isEmpty()) {
            int[] at = stack.pop();

            area.add(at);

            for (int[] delta : DIRECTIONS) {
                int nx = at[0] + delta[0];
                int ny = at[1] + delta[1];

                if (!this.arena.gameTile(nx, ny)) return List.of();
                if (this.states[nx][ny] == SEALED && this.colours[nx][ny] == team) continue;
                if (seen.add(nx * 1000 + ny)) stack.push(new int[] {nx, ny});
            }
        }

        return area;
    }

    private void clean(int x, int y) {
        if (!this.arena.gameTile(x, y) || this.states[x][y] == DEFAULT) return;

        this.points[x][y].clear();
        this.colours[x][y] = -1;
        this.states[x][y] = DEFAULT;
    }

    // ---- The power-ups

    private void spawnPowerUp(long time) {
        List<Integer> kinds = new ArrayList<>(this.rules.powers());

        if (kinds.isEmpty()) return;
        if (kinds.size() >= 2) kinds.add(QUESTION_MARK);

        List<int[]> free = new ArrayList<>();

        for (int x = 0; x < this.arena.width(); x++) {
            for (int y = 0; y < this.arena.height(); y++) {
                if (this.arena.gameTile(x, y) && this.nobodyOn(x, y) && this.powerAt(x, y) == null)
                    free.add(new int[] {x, y});
            }
        }

        if (free.isEmpty()) return;

        int[] tile = free.get(this.random.nextInt(free.size()));
        int type = kinds.get(this.random.nextInt(kinds.size()));

        this.powerUps.add(
                new PowerUp(this.nextObjectId++, type, tile[0], tile[1], time + 20_000L + this.random.nextInt(10_001)));
    }

    /** The cannon: the player flies to the end of the line ahead, sealing it; whoever is in the way is knocked aside. */
    private void cannon(Player player, long now) {
        List<int[]> line = this.line(player.x, player.y, player.dir);

        if (line.isEmpty()) line = List.of(new int[] {player.x, player.y});

        for (int[] tile : line) {
            for (Player other : this.players) {
                if (other == player || other.x != tile[0] || other.y != tile[1]) continue;

                int[] aside = this.aside(other, player.dir);

                if (aside != null) {
                    other.x = aside[0];
                    other.y = aside[1];
                }

                this.stun(other, PlayerState.STUNNED, now);
            }

            int x = tile[0];
            int y = tile[1];

            if (!this.arena.gameTile(x, y) || (this.states[x][y] == SEALED && this.colours[x][y] == player.team))
                continue;

            this.score(x, y, SEALED, player.team, player.userId);
            this.colours[x][y] = player.team;
            this.states[x][y] = SEALED;
            this.fill(x, y, player.team, player.userId, now);
        }

        int[] last = line.get(line.size() - 1);
        int fromX = player.x;
        int fromY = player.y;

        player.x = last[0];
        player.y = last[1];
        player.goalX = -1;
        player.goalY = -1;
        this.setState(player, PlayerState.FLYING, now + FLYING_MS);
        this.event("cannon", player.userId, fromX, fromY, last[0], last[1], now);
    }

    private int[] aside(Player player, int dir) {
        for (int turn : new int[] {2, 6}) {
            int[] delta = DIRECTIONS[(dir + turn) % 8];
            int x = player.x + delta[0];
            int y = player.y + delta[1];

            if (this.arena.walkable(x, y) && this.nobodyOn(x, y)) return new int[] {x, y};
        }

        return null;
    }

    /** The box of pins: pins scattered six tiles ahead; whoever stands on one has their ball burst. */
    private void boxOfPins(Player player, long now) {
        int[] delta = DIRECTIONS[player.dir];
        int cx = player.x + 6 * delta[0];
        int cy = player.y + 6 * delta[1];
        int most = 8 + this.random.nextInt(8);
        int placed = 0;
        List<int[]> spots = circle(cx, cy, 3);

        Collections.shuffle(spots, this.random);

        for (int[] spot : spots) {
            if (placed >= most || !this.arena.walkable(spot[0], spot[1])) continue;
            if (spot[0] == player.x && spot[1] == player.y) continue;
            if (!this.random.nextBoolean()) continue;

            this.pins.add(new Pin(this.nextObjectId++, spot[0], spot[1], now + 12_000L + this.random.nextInt(4001)));
            placed++;

            for (Player other : this.players) {
                if (other.x == spot[0] && other.y == spot[1]) this.stun(other, PlayerState.BALL_BROKEN, now);
            }
        }
    }

    /** The bomb: the tiles around turn grey again, everybody near is stunned and blown a tile away (the bomber too). */
    private void bomb(Player player, long now) {
        for (int[] tile : circle(player.x, player.y, 3)) this.clean(tile[0], tile[1]);

        List<Player> hit = new ArrayList<>();

        for (int[] tile : circle(player.x, player.y, 7)) {
            for (Player other : this.players) {
                if (other.x == tile[0] && other.y == tile[1]) hit.add(other);
            }
        }

        if (!hit.contains(player)) hit.add(player);

        for (Player other : hit) {
            if (other != player) {
                int away = direction(player.x, player.y, other.x, other.y);
                int x = other.x + DIRECTIONS[away][0];
                int y = other.y + DIRECTIONS[away][1];

                if (this.arena.walkable(x, y) && this.nobodyOn(x, y)) {
                    other.x = x;
                    other.y = y;
                }
            }

            this.stun(other, PlayerState.STUNNED, now);
        }
    }

    private void stun(Player player, PlayerState kind, long now) {
        player.goalX = -1;
        player.goalY = -1;
        player.harlequinBy = null;
        this.setState(player, kind, now + STUN_MS);
        this.event("stun", player.userId, kind == PlayerState.BALL_BROKEN ? 1 : 0, player.x, player.y, 0, now);
    }

    private void setState(Player player, PlayerState kind, long until) {
        player.state = kind;
        player.stateUntil = until;
    }

    /** The tiles on a straight line ahead, up to where one cannot bounce any more. */
    private List<int[]> line(int x, int y, int dir) {
        List<int[]> tiles = new ArrayList<>();
        int[] delta = DIRECTIONS[dir];
        int cx = x;
        int cy = y;

        while (true) {
            int nx = cx + delta[0];
            int ny = cy + delta[1];

            if (!this.arena.walkable(nx, ny)) break;

            tiles.add(new int[] {nx, ny});
            cx = nx;
            cy = ny;
        }

        return tiles;
    }

    /** Kepler's "circle": the tiles whose squared distance is at most the radius given. */
    static List<int[]> circle(int x, int y, int radius) {
        List<int[]> tiles = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx * dx + dy * dy <= radius) tiles.add(new int[] {x + dx, y + dy});
            }
        }

        return tiles;
    }

    static int direction(int x, int y, int toX, int toY) {
        int dx = Integer.signum(toX - x);
        int dy = Integer.signum(toY - y);

        for (int dir = 0; dir < DIRECTIONS.length; dir++) {
            if (DIRECTIONS[dir][0] == dx && DIRECTIONS[dir][1] == dy) return dir;
        }

        return 2;
    }

    // ---- Robots

    /**
     * A robot thinks every half second or so: it uses its power-up when it is worth it, goes for a power-up close by,
     * and otherwise bounces to the tile that brings its team the most, near it.
     */
    private void botThinks(Player bot, long time) {
        bot.thinkAt = time + 300L + this.random.nextInt(400);

        if (!bot.canAct()) return;

        if (bot.held != 0 && this.botUsesPower(bot, time)) return;

        boolean idle = bot.goalX < 0 || (bot.x == bot.goalX && bot.y == bot.goalY);

        if (!idle && this.random.nextInt(100) >= 15) return;

        PowerUp near = null;

        for (PowerUp power : this.powerUps) {
            int distance = Math.max(Math.abs(power.x() - bot.x), Math.abs(power.y() - bot.y));

            if (distance <= 7 && bot.held == 0 && (near == null || this.random.nextBoolean())) near = power;
        }

        if (near != null && this.random.nextInt(100) < 70) {
            bot.goalX = near.x();
            bot.goalY = near.y();
            return;
        }

        int team = bot.paintsFor();
        double best = Double.NEGATIVE_INFINITY;
        int goalX = -1;
        int goalY = -1;

        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                int x = bot.x + dx;
                int y = bot.y + dy;

                if (!this.arena.gameTile(x, y) || (dx == 0 && dy == 0) || !this.nobodyOn(x, y)) continue;

                int tileState = this.states[x][y];
                double value;

                if (bot.state == PlayerState.CLEANING_TILES) {
                    value = this.colours[x][y] >= 0 && this.colours[x][y] != bot.team ? 6 + tileState : -5;
                } else if (tileState == SEALED) {
                    value = -5;
                } else if (this.colours[x][y] == team) {
                    value = 5 + tileState;
                } else if (tileState == DEFAULT) {
                    value = 5;
                } else {
                    value = 4 + tileState;
                }

                value -= 0.6 * Math.max(Math.abs(dx), Math.abs(dy)) + this.random.nextDouble() * 2.5;

                if (value > best) {
                    best = value;
                    goalX = x;
                    goalY = y;
                }
            }
        }

        if (goalX >= 0) {
            bot.goalX = goalX;
            bot.goalY = goalY;
        }
    }

    private boolean botUsesPower(Player bot, long time) {
        switch (bot.held) {
            case FLASHLIGHT, CANNON -> {
                int bestDir = bot.dir;
                int bestCount = this.worthAhead(bot, bot.dir);

                for (int dir = 0; dir < 8; dir++) {
                    int count = this.worthAhead(bot, dir);

                    if (count > bestCount) {
                        bestCount = count;
                        bestDir = dir;
                    }
                }

                if (bestCount < 3) return false;

                if (bestDir == bot.dir) {
                    this.usePower(bot.userId, time);
                } else {
                    // A step that way turns the ball; the power-up goes next time.
                    int[] delta = DIRECTIONS[bestDir];

                    if (this.arena.walkable(bot.x + delta[0], bot.y + delta[1])) {
                        bot.goalX = bot.x + delta[0];
                        bot.goalY = bot.y + delta[1];
                    }
                }

                return true;
            }
            case BOX_OF_PINS -> {
                int[] delta = DIRECTIONS[bot.dir];
                int cx = bot.x + 6 * delta[0];
                int cy = bot.y + 6 * delta[1];

                for (Player other : this.players) {
                    if (other.team != bot.team && Math.abs(other.x - cx) <= 2 && Math.abs(other.y - cy) <= 2) {
                        this.usePower(bot.userId, time);
                        return true;
                    }
                }

                if (time + 3000L >= bot.heldUntil) this.usePower(bot.userId, time);

                return false;
            }
            default -> {
                if (this.random.nextInt(100) < 45) {
                    this.usePower(bot.userId, time);
                    return true;
                }

                return false;
            }
        }
    }

    private int worthAhead(Player bot, int dir) {
        int count = 0;

        for (int[] tile : this.line(bot.x, bot.y, dir)) {
            if (this.arena.gameTile(tile[0], tile[1])
                    && !(this.states[tile[0]][tile[1]] == SEALED && this.colours[tile[0]][tile[1]] == bot.team)) {
                count++;
            }
        }

        return count;
    }

    // ---- Helpers

    @SuppressWarnings("unchecked")
    private void clearTiles() {
        int width = this.arena.width();
        int height = this.arena.height();

        this.colours = new int[width][height];
        this.states = new int[width][height];
        this.points = new List[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                this.colours[x][y] = this.arena.gameTile(x, y) ? -1 : -2;
                this.points[x][y] = new ArrayList<>();
            }
        }
    }

    private int freeTeam(int wanted) {
        if (wanted >= 0 && wanted < this.rules.teams() && this.teamSize(wanted) < this.rules.perTeam()) return wanted;

        int best = -1;

        for (int team = 0; team < this.rules.teams(); team++) {
            if (this.teamSize(team) < this.rules.perTeam() && (best < 0 || this.teamSize(team) < this.teamSize(best))) {
                best = team;
            }
        }

        if (best < 0) throw new BattleBallException("table_full");

        return best;
    }

    public int teamSize(int team) {
        int size = 0;

        for (Player player : this.players) {
            if (player.team == team) size++;
        }

        return size;
    }

    public int[] teamScores() {
        int[] scores = new int[4];

        for (int x = 0; x < this.arena.width(); x++) {
            for (int y = 0; y < this.arena.height(); y++) {
                for (Points entry : this.points[x][y]) {
                    if (entry.team() >= 0 && entry.team() < 4) scores[entry.team()] += entry.points();
                }
            }
        }

        return scores;
    }

    public int playerScore(int userId) {
        int score = 0;

        for (int x = 0; x < this.arena.width(); x++) {
            for (int y = 0; y < this.arena.height(); y++) {
                for (Points entry : this.points[x][y]) {
                    if (entry.userId() == userId) score += entry.points();
                }
            }
        }

        return score;
    }

    private boolean nobodyOn(int x, int y) {
        for (Player player : this.players) {
            if (player.x == x && player.y == y) return false;
        }

        return true;
    }

    private PowerUp powerAt(int x, int y) {
        for (PowerUp power : this.powerUps) {
            if (power.x() == x && power.y() == y) return power;
        }

        return null;
    }

    private void event(String type, int userId, int a, int b, int c, int d, long at) {
        this.log.addLast(new Event(++this.seq, at, type, userId, a, b, c, d));

        while (this.log.size() > LOG_SIZE) this.log.pollFirst();
    }

    private Player active(int userId) {
        Player player = this.player(userId);

        if (player == null) throw new BattleBallException("no_table");
        if (this.state != State.PLAYING) throw new BattleBallException("not_playing");

        return player;
    }

    private void requireHost(int userId) {
        if (this.hostId() != userId) throw new BattleBallException("not_host");
    }

    private Player byName(String name) {
        for (Player player : this.players) {
            if (player.name.equals(name)) return player;
        }

        return null;
    }

    public Player player(int userId) {
        for (Player player : this.players) {
            if (player.userId == userId) return player;
        }

        return null;
    }

    /** The host: the player who came first among those still here. */
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

    /** The tiles as one character each, row by row: '-' not a game tile, '0' grey, then 'a' + team * 4 + state - 1. */
    public String tileString() {
        StringBuilder text = new StringBuilder(this.arena.width() * this.arena.height());

        for (int y = 0; y < this.arena.height(); y++) {
            for (int x = 0; x < this.arena.width(); x++) {
                if (!this.arena.gameTile(x, y)) text.append('-');
                else if (this.states[x][y] == DEFAULT || this.colours[x][y] < 0) text.append('0');
                else text.append((char) ('a' + this.colours[x][y] * 4 + this.states[x][y] - 1));
            }
        }

        return text.toString();
    }

    public int tileState(int x, int y) {
        return this.states[x][y];
    }

    public int tileColour(int x, int y) {
        return this.colours[x][y];
    }

    public int id() {
        return this.id;
    }

    public BattleBallRules rules() {
        return this.rules;
    }

    public BattleBallArena arena() {
        return this.arena;
    }

    public State state() {
        return this.state;
    }

    public long phaseEndsAt() {
        return this.phaseEndsAt;
    }

    public List<Player> players() {
        return this.players;
    }

    public List<PowerUp> powerUps() {
        return this.powerUps;
    }

    public List<Pin> pins() {
        return this.pins;
    }

    public List<Integer> winners() {
        return this.winners;
    }

    public List<Event> log() {
        return new ArrayList<>(this.log);
    }

    /** Whether the finished game still has to be written to the scores (true once). */
    public boolean takeRecord() {
        if (this.state != State.FINISHED || this.recorded) return false;

        this.recorded = true;
        return true;
    }

    /** For the tests: puts a power-up on a tile. */
    void dropPowerUp(int type, int x, int y) {
        this.powerUps.add(new PowerUp(this.nextObjectId++, type, x, y, this.clock + 30_000L));
    }

    /** For the tests: moves a player. */
    void place(int userId, int x, int y, int dir) {
        Player player = this.player(userId);

        player.x = x;
        player.y = y;
        player.dir = dir;
    }
}
