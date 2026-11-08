package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * HabboKart: a kart race of three laps round {@link KartTrack}, up to eight karts. Each window drives its own kart and
 * says now and then where it is and how far it went (in laps); the race keeps the order, the item boxes, the banana
 * peels left on the road and the shells thrown. Robots drive along the road themselves.
 *
 * <p>Items: a box gives one (a turbo, a banana peel, a shell; the last ones get more shells and turbos). A peel lies
 * where it was dropped and spins the first kart over it; a shell flies to the kart just ahead and spins it. A box comes
 * back {@link #BOX_BACK_MS} after it was taken.
 */
public final class KartRace implements HallGame {

    static final int LAPS = 3;
    static final long COUNTDOWN_MS = 4000L;
    static final long MAX_MS = 300_000L;
    static final long BOX_BACK_MS = 2500L;
    static final long SPIN_MS = 1400L;
    static final long SHELL_MS = 1100L;
    static final long BOOST_MS = 1300L;
    static final int KARTS = 5;
    /** The fastest a kart goes (turbo included), pixels a second: what a report may claim. */
    static final double MAX_SPEED = 620;

    static final double PICK_RADIUS = 70;
    static final double PEEL_RADIUS = 30;
    static final String[] ITEMS = {"boost", "banana", "shell"};
    /** The rows of boxes: where along a lap, and their places across the road. */
    static final double[] BOX_ROWS = {0.18, 0.47, 0.76};

    static final double[] BOX_SIDES = {-72, -24, 24, 72};

    private static final KartTrack TRACK = KartTrack.MAIN;

    static final class Car {
        double x;
        double y;
        double angle;
        /** How far it went, in laps (negative on the grid, behind the line). */
        double progress;

        long reportedAt;
        boolean robot;
        double pace;
        double side;
        int kart;
        String item = "";
        long itemAt;
        long spinUntil;
        long boostUntil;
        int place;
        long finishedAt = -1;
        boolean out;
    }

    record Peel(int id, double x, double y) {}

    record Shell(int id, int from, int to, double x, double y, long thrownAt) {}

    private final List<Car> cars = new ArrayList<>();
    private final List<Peel> peels = new ArrayList<>();
    private final List<Shell> shells = new ArrayList<>();
    private final long[] boxTaken = new long[BOX_ROWS.length * BOX_SIDES.length];
    private Random random;
    private long startAt = -1;
    private long clock = -1;
    private int arrived;
    private int nextId = 1;
    private boolean finished;
    private List<Integer> winners = List.of();

    @Override
    public void start(int seats, Random random) {
        this.random = random;
        this.cars.clear();
        this.peels.clear();
        this.shells.clear();

        for (int seat = 0; seat < seats; seat++) {
            Car car = new Car();
            double[] grid = grid(seat);

            car.x = grid[0];
            car.y = grid[1];
            car.angle = grid[2];
            car.progress = grid[3];
            car.kart = seat % KARTS;
            car.pace = 330 + random.nextDouble() * 50;
            car.side = (seat % 2 == 0 ? -1 : 1) * (20 + random.nextInt(50));
            this.cars.add(car);
        }

        Arrays.fill(this.boxTaken, -1);
        this.startAt = -1;
        this.clock = -1;
        this.arrived = 0;
        this.finished = false;
        this.winners = List.of();
    }

    /** A seat's place on the grid, two by two behind the line: (x, y, heading, progress in laps). */
    static double[] grid(int seat) {
        double back = 60 + (seat / 2) * 70;
        double[] at = TRACK.pointAt(-back, seat % 2 == 0 ? -45 : 45);

        return new double[] {at[0], at[1], at[2], -back / TRACK.length()};
    }

    double[] box(int index) {
        double row = BOX_ROWS[index / BOX_SIDES.length];

        return TRACK.pointAt(row * TRACK.length(), BOX_SIDES[index % BOX_SIDES.length]);
    }

    private boolean boxThere(int index) {
        return this.boxTaken[index] < 0 || this.clock - this.boxTaken[index] >= BOX_BACK_MS;
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

            this.drive(seat, car, seconds, random);
        }

        this.flyShells(now);

        if (now - this.startAt >= MAX_MS) this.end();
    }

    /** A robot: along the road at its pace, a box picked when passed, its item used a little later. */
    private void drive(int seat, Car car, double seconds, Random random) {
        if (this.clock < car.spinUntil) return;

        double speed = car.pace * (this.clock < car.boostUntil ? 1.45 : 1) * (0.92 + random.nextDouble() * 0.16);
        double before = car.progress * TRACK.length();
        double after = before + speed * seconds;
        double[] at = TRACK.pointAt(after, car.side);

        car.side = Math.max(-80, Math.min(80, car.side + (random.nextDouble() - 0.5) * 30 * seconds));
        car.x = at[0];
        car.y = at[1];
        car.angle = at[2];
        car.progress = after / TRACK.length();

        for (int index = 0; index < this.boxTaken.length && car.item.isEmpty(); index++) {
            double[] box = this.box(index);

            if (this.boxThere(index) && Math.hypot(box[0] - car.x, box[1] - car.y) < PICK_RADIUS)
                this.pick(seat, index);
        }

        if (!car.item.isEmpty() && this.clock - car.itemAt > 1200 + random.nextInt(3000)) this.use(seat);

        Iterator<Peel> peels = this.peels.iterator();

        while (peels.hasNext()) {
            Peel peel = peels.next();

            if (Math.hypot(peel.x() - car.x, peel.y() - car.y) < PEEL_RADIUS) {
                peels.remove();
                car.spinUntil = this.clock + SPIN_MS;
            }
        }

        if (car.progress >= LAPS) this.arrive(seat);
    }

    private void flyShells(long now) {
        Iterator<Shell> flying = this.shells.iterator();

        while (flying.hasNext()) {
            Shell shell = flying.next();

            if (now - shell.thrownAt() < SHELL_MS) continue;

            flying.remove();

            if (shell.to() >= 0) {
                Car target = this.cars.get(shell.to());

                if (target.finishedAt < 0 && !target.out) target.spinUntil = now + SPIN_MS;
            }
        }
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

        if (everybody || this.arrived >= this.cars.size()) this.end();
    }

    private void end() {
        if (this.finished) return;

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

    /** The seat a box gives an item to: an item drawn, better for those behind. */
    private void pick(int seat, int index) {
        Car car = this.cars.get(seat);

        this.boxTaken[index] = this.clock;
        car.itemAt = this.clock;

        int rank = this.rank(seat);
        int roll = this.random.nextInt(100);
        boolean behind = rank > this.cars.size() / 2;

        car.item = behind
                ? (roll < 40 ? "shell" : roll < 80 ? "boost" : "banana")
                : (roll < 45 ? "banana" : roll < 75 ? "boost" : "shell");
    }

    private int rank(int seat) {
        int rank = 1;
        Car car = this.cars.get(seat);

        for (Car other : this.cars) {
            if (other != car && other.progress > car.progress) rank++;
        }

        return rank;
    }

    private void use(int seat) {
        Car car = this.cars.get(seat);

        switch (car.item) {
            case "boost" -> car.boostUntil = this.clock + BOOST_MS;
            case "banana" -> {
                double[] behind = TRACK.pointAt(car.progress * TRACK.length() - 40, 0);
                double x = car.x - Math.cos(car.angle) * 40;
                double y = car.y - Math.sin(car.angle) * 40;

                this.peels.add(new Peel(
                        this.nextId++, Double.isFinite(x) ? x : behind[0], Double.isFinite(y) ? y : behind[1]));

                if (this.peels.size() > 24) this.peels.remove(0);
            }
            case "shell" -> {
                int target = -1;
                double gap = Double.MAX_VALUE;

                for (int other = 0; other < this.cars.size(); other++) {
                    Car candidate = this.cars.get(other);
                    double ahead = candidate.progress - car.progress;

                    if (other != seat && !candidate.out && candidate.finishedAt < 0 && ahead > 0 && ahead < gap) {
                        gap = ahead;
                        target = other;
                    }
                }

                this.shells.add(new Shell(this.nextId++, seat, target, car.x, car.y, this.clock));
            }
            default -> {
                return;
            }
        }

        car.item = "";
    }

    @Override
    public void play(int seat, JsonObject move) {
        if (!this.running()) throw new HallException("not_started");

        Car car = this.cars.get(seat);

        if (car.finishedAt >= 0 || car.out) throw new HallException("finished");
        if (move == null) throw new HallException("bad_move");

        if (move.has("kart")) car.kart = Math.floorMod(TurnGame.intOf(move, "kart"), KARTS);

        if (move.has("x") && move.has("y") && move.has("progress")) {
            double x = move.get("x").getAsDouble();
            double y = move.get("y").getAsDouble();
            double progress = move.get("progress").getAsDouble();

            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(progress))
                throw new HallException("bad_move");

            long since = Math.max(50, this.clock - Math.max(car.reportedAt, this.startAt));
            double most = (since / 1000.0 * MAX_SPEED + 60) / TRACK.length();

            car.x = Math.max(-500, Math.min(4200, x));
            car.y = Math.max(-500, Math.min(3300, y));
            car.angle = move.has("angle") ? move.get("angle").getAsDouble() : car.angle;
            car.progress = Math.min(progress, car.progress + most);
            car.reportedAt = this.clock;
        }

        if (move.has("pick") && car.item.isEmpty()) {
            int index = TurnGame.intOf(move, "pick");

            if (index >= 0 && index < this.boxTaken.length && this.boxThere(index)) {
                double[] box = this.box(index);

                if (Math.hypot(box[0] - car.x, box[1] - car.y) < PICK_RADIUS * 1.6) this.pick(seat, index);
            }
        }

        if (move.has("hit")) {
            int id = TurnGame.intOf(move, "hit");

            if (this.peels.removeIf(peel -> peel.id() == id)) car.spinUntil = this.clock + SPIN_MS;
        }

        if (move.has("use") && move.get("use").getAsBoolean()) this.use(seat);

        if (car.progress >= LAPS) this.arrive(seat);
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
        car.kart = random.nextInt(KARTS);
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
        long now = Math.max(this.clock, 0);

        view.addProperty("turn", -1);
        view.addProperty("laps", LAPS);
        view.addProperty("width", KartTrack.WIDTH);
        view.addProperty("startsIn", this.startAt < 0 ? COUNTDOWN_MS : Math.max(0L, this.startAt - now));
        view.addProperty("elapsed", this.startAt < 0 ? 0L : Math.max(0L, now - this.startAt));
        view.addProperty(
                "timeLeft",
                this.startAt < 0 ? MAX_MS : Math.max(0L, this.startAt + MAX_MS - Math.max(now, this.startAt)));

        JsonArray boxes = new JsonArray();

        for (int index = 0; index < this.boxTaken.length; index++) boxes.add(this.boxThere(index));

        view.add("boxes", boxes);

        JsonArray peels = new JsonArray();

        for (Peel peel : this.peels) {
            JsonObject entry = new JsonObject();

            entry.addProperty("id", peel.id());
            entry.addProperty("x", Math.round(peel.x()));
            entry.addProperty("y", Math.round(peel.y()));
            peels.add(entry);
        }

        view.add("peels", peels);

        JsonArray shells = new JsonArray();

        for (Shell shell : this.shells) {
            JsonObject entry = new JsonObject();

            entry.addProperty("id", shell.id());
            entry.addProperty("to", shell.to());
            entry.addProperty("x", Math.round(shell.x()));
            entry.addProperty("y", Math.round(shell.y()));
            entry.addProperty("flown", Math.min(SHELL_MS, now - shell.thrownAt()));
            entry.addProperty("flight", SHELL_MS);
            shells.add(entry);
        }

        view.add("shells", shells);

        JsonArray list = new JsonArray();

        for (int index = 0; index < this.cars.size(); index++) {
            Car car = this.cars.get(index);
            JsonObject entry = new JsonObject();
            double[] start = grid(index);

            entry.addProperty("x", Math.round(car.x * 10) / 10.0);
            entry.addProperty("y", Math.round(car.y * 10) / 10.0);
            entry.addProperty("angle", Math.round(car.angle * 1000) / 1000.0);
            entry.addProperty("progress", Math.round(car.progress * 10000) / 10000.0);
            entry.addProperty("lap", Math.max(1, Math.min(LAPS, 1 + (int) Math.floor(car.progress))));
            entry.addProperty("place", car.place);
            entry.addProperty("time", car.finishedAt < 0 || this.startAt < 0 ? -1 : car.finishedAt - this.startAt);
            entry.addProperty("robot", car.robot);
            entry.addProperty("out", car.out);
            entry.addProperty("kart", car.kart);
            entry.addProperty("item", car.item);
            entry.addProperty("spin", Math.max(0L, car.spinUntil - now));
            entry.addProperty("boost", Math.max(0L, car.boostUntil - now));
            entry.addProperty("startX", start[0]);
            entry.addProperty("startY", start[1]);
            entry.addProperty("startAngle", start[2]);
            entry.addProperty("startProgress", start[3]);
            list.add(entry);
        }

        view.add("cars", list);
        return view;
    }

    Car car(int seat) {
        return this.cars.get(seat);
    }

    static double lapLength() {
        return TRACK.length();
    }
}
