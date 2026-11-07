package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * HabboKart: a kart race on a circuit of the hotel's own track pieces (sporttrack), each player in one of the hotel's
 * car effects. Each window drives its own car (arrows) and tells the race where it is now and then; the race keeps
 * the order of arrival. Robots follow the middle of the road at their own pace.
 *
 * <p>The circuit is a loop of 2 x 2 blocks (the size of a track piece), {@link #TRACK}, the finish line on the first
 * one (in the middle of the top straight, the grid behind it); a lap is {@code TRACK.length} blocks, the race
 * {@link #LAPS} laps. The progress of a car is counted in blocks from the line, the middle of a block to the next.
 */
public final class KartRace implements HallGame {

    /** The loop of blocks (x, y), driven in this order; the finish line on the first. */
    static final int[][] TRACK = {
        {4, 0}, {5, 0}, {6, 0}, {7, 0}, {7, 1}, {7, 2}, {7, 3}, {7, 4}, {7, 5}, {6, 5}, {5, 5}, {4, 5}, {4, 4}, {4, 3},
        {3, 3}, {2, 3}, {1, 3}, {0, 3}, {0, 2}, {0, 1}, {0, 0}, {1, 0}, {2, 0}, {3, 0}
    };

    static final int LAPS = 3;
    static final long COUNTDOWN_MS = 4000L;
    static final long MAX_MS = 240_000L;
    /** Robots' speed, in blocks a second. */
    private static final double ROBOT_SPEED = 2.05;

    static final class Car {
        double x;
        double y;
        double angle;
        double progress;
        boolean robot;
        double pace;
        int place;
        long finishedAt = -1;
        boolean out;
    }

    private final List<Car> cars = new ArrayList<>();
    private final Set<Integer> robots = new HashSet<>();
    private Random random;
    private long startAt = -1;
    private long clock = -1;
    private int arrived;
    private boolean finished;
    private List<Integer> winners = List.of();

    @Override
    public void start(int seats, Random random) {
        this.random = random;
        this.cars.clear();
        this.robots.clear();

        for (int seat = 0; seat < seats; seat++) {
            Car car = new Car();
            double[] grid = grid(seat);

            car.x = grid[0];
            car.y = grid[1];
            car.angle = grid[2];
            car.progress = grid[3];
            car.pace = ROBOT_SPEED * (0.9 + random.nextDouble() * 0.2);
            this.cars.add(car);
        }

        this.startAt = -1;
        this.clock = -1;
        this.arrived = 0;
        this.finished = false;
        this.winners = List.of();
    }

    /** A seat's place on the grid, behind the line: (x, y, angle, progress), two by two. */
    static double[] grid(int seat) {
        double progress = -0.35 - (seat / 2) * 0.5;
        double[] centre = centreAt(progress, seat % 2 == 0 ? -0.45 : 0.45);

        return new double[] {centre[0], centre[1], centre[2], progress};
    }

    /** The middle of the road at a progress (blocks since the start), with the direction of the road there. */
    static double[] centreAt(double progress, double lane) {
        int n = TRACK.length;
        double wrapped = ((progress % n) + n) % n;
        int index = (int) Math.floor(wrapped);
        double frac = wrapped - index;
        int[] from = TRACK[index];
        int[] to = TRACK[(index + 1) % n];
        double fx = from[0] * 2 + 1;
        double fy = from[1] * 2 + 1;
        double dx = (to[0] - from[0]) * 2;
        double dy = (to[1] - from[1]) * 2;
        double angle = Math.atan2(dy, dx);

        return new double[] {fx + dx * frac - Math.sin(angle) * lane, fy + dy * frac + Math.cos(angle) * lane, angle};
    }

    @Override
    public void tick(long now, Random random) {
        long previous = this.clock;

        this.clock = now;

        if (this.startAt < 0) this.startAt = now + COUNTDOWN_MS;
        if (this.finished || previous < 0 || now <= this.startAt) return;

        double seconds = (now - Math.max(previous, this.startAt)) / 1000.0;

        for (int seat = 0; seat < this.cars.size(); seat++) {
            Car car = this.cars.get(seat);

            if (!car.robot || car.out || car.finishedAt >= 0) continue;

            car.progress += car.pace * seconds * (0.85 + random.nextDouble() * 0.3);

            double[] centre = centreAt(car.progress, (seat % 3 - 1) * 0.45);

            car.x = centre[0];
            car.y = centre[1];
            car.angle = centre[2];

            if (car.progress >= LAPS * TRACK.length) this.arrive(seat);
        }

        if (now - this.startAt >= MAX_MS) this.end();
    }

    private boolean running() {
        return !this.finished && this.startAt >= 0 && this.clock >= this.startAt;
    }

    private void arrive(int seat) {
        Car car = this.cars.get(seat);

        if (car.finishedAt >= 0) return;

        car.finishedAt = this.clock;
        car.place = ++this.arrived;

        boolean everybody = true;

        for (Car other : this.cars) {
            if (!other.out && other.finishedAt < 0 && !other.robot) everybody = false;
        }

        // Once every player has arrived (robots aside), or everybody, the race is over.
        if (everybody || this.arrived >= this.cars.size()) this.end();
    }

    private void end() {
        if (this.finished) return;

        // Those still on the road are ranked by how far they went.
        List<Integer> order = new ArrayList<>();

        for (int seat = 0; seat < this.cars.size(); seat++) {
            if (this.cars.get(seat).finishedAt < 0 && !this.cars.get(seat).out) order.add(seat);
        }

        order.sort((a, b) -> Double.compare(this.cars.get(b).progress, this.cars.get(a).progress));

        for (int seat : order) this.cars.get(seat).place = ++this.arrived;

        for (int seat = 0; seat < this.cars.size(); seat++) {
            if (this.cars.get(seat).place == 1) this.winners = List.of(seat);
        }

        this.finished = true;
    }

    @Override
    public void play(int seat, JsonObject move) {
        if (!this.running()) throw new HallException("not_started");

        Car car = this.cars.get(seat);

        if (car.finishedAt >= 0 || car.out) throw new HallException("finished");
        if (move == null || !move.has("x") || !move.has("y") || !move.has("progress"))
            throw new HallException("bad_move");

        double x = move.get("x").getAsDouble();
        double y = move.get("y").getAsDouble();
        double progress = move.get("progress").getAsDouble();

        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(progress))
            throw new HallException("bad_move");

        car.x = Math.max(-2, Math.min(18, x));
        car.y = Math.max(-2, Math.min(14, y));
        car.angle = move.has("angle") ? move.get("angle").getAsDouble() : car.angle;
        // A car goes round, it does not jump: three blocks at most between two news.
        car.progress = Math.min(progress, car.progress + 3);

        if (car.progress >= LAPS * TRACK.length) this.arrive(seat);
    }

    /** A player drives while the race is on; a robot (or a player asleep at the wheel) is driven by the race. */
    @Override
    public boolean waitingFor(int seat) {
        if (!this.running() || seat >= this.cars.size()) return false;

        Car car = this.cars.get(seat);

        return !car.robot && !car.out && car.finishedAt < 0;
    }

    @Override
    public void bot(int seat, Random random) {
        Car car = this.cars.get(seat);

        car.robot = true;
        this.robots.add(seat);
    }

    @Override
    public void forfeit(int seat) {
        if (seat >= this.cars.size() || this.finished) return;

        this.cars.get(seat).out = true;

        int left = 0;

        for (Car car : this.cars) {
            if (!car.out) left++;
        }

        if (left <= 1) this.end();
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
        view.addProperty("laps", LAPS);
        view.addProperty("startsIn", this.startAt < 0 ? COUNTDOWN_MS : Math.max(0L, this.startAt - this.clock));
        view.addProperty(
                "timeLeft",
                this.startAt < 0 ? MAX_MS : Math.max(0L, this.startAt + MAX_MS - Math.max(this.clock, this.startAt)));

        JsonArray track = new JsonArray();

        for (int[] block : TRACK) {
            JsonArray entry = new JsonArray();

            entry.add(block[0]);
            entry.add(block[1]);
            track.add(entry);
        }

        view.add("track", track);

        JsonArray list = new JsonArray();

        for (int index = 0; index < this.cars.size(); index++) {
            Car car = this.cars.get(index);
            JsonObject entry = new JsonObject();
            double[] start = grid(index);

            entry.addProperty("x", Math.round(car.x * 1000) / 1000.0);
            entry.addProperty("y", Math.round(car.y * 1000) / 1000.0);
            entry.addProperty("angle", Math.round(car.angle * 1000) / 1000.0);
            entry.addProperty("progress", Math.round(car.progress * 1000) / 1000.0);
            entry.addProperty("lap", Math.max(1, Math.min(LAPS, 1 + (int) Math.floor(car.progress / TRACK.length))));
            entry.addProperty("place", car.place);
            entry.addProperty("time", car.finishedAt < 0 || this.startAt < 0 ? -1 : car.finishedAt - this.startAt);
            entry.addProperty("robot", car.robot);
            entry.addProperty("out", car.out);
            entry.addProperty("startX", start[0]);
            entry.addProperty("startY", start[1]);
            list.add(entry);
        }

        view.add("cars", list);
        return view;
    }

    Car car(int seat) {
        return this.cars.get(seat);
    }
}
