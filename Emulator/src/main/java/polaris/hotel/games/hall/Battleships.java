package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Habbo's Battleships (the Cunning Fox Gamehall, 2004; rules as Kepler plays them): a sea of 13 by 13 each, one
 * aircraft carrier (5), two battleships (4), three cruisers (3) and four destroyers (2), placed by each player at the
 * same time, ships never touching. Then the players shoot in turns at the other's sea; a hit shoots again, a miss
 * passes the turn. The first to sink every ship of the other wins.
 */
public final class Battleships extends TurnGame {

    public static final int SIZE = 13;
    /** The ships' lengths, as the original gives them. */
    public static final int[] FLEET = {5, 4, 4, 3, 3, 3, 2, 2, 2, 2};

    record Ship(int x, int y, int length, boolean vertical) {
        boolean covers(int cx, int cy) {
            return this.vertical
                    ? cx == this.x && cy >= this.y && cy < this.y + this.length
                    : cy == this.y && cx >= this.x && cx < this.x + this.length;
        }
    }

    private final List<List<Ship>> fleets = new ArrayList<>();
    /** Shots at each seat's sea: 0 none, 1 miss, 2 hit. */
    private final int[][][] shots = new int[2][SIZE][SIZE];

    private boolean placing;
    private int lastX = -1;
    private int lastY = -1;
    private int lastSeat = -1;
    private String lastResult = "";

    @Override
    public void start(int seats, Random random) {
        super.start(seats, random);
        this.fleets.clear();
        this.fleets.add(null);
        this.fleets.add(null);

        for (int[][] sea : this.shots) {
            for (int[] row : sea) java.util.Arrays.fill(row, 0);
        }

        this.placing = true;
        this.lastX = -1;
        this.lastY = -1;
        this.lastSeat = -1;
        this.lastResult = "";
    }

    @Override
    public boolean waitingFor(int seat) {
        if (this.finished) return false;

        return this.placing ? this.fleets.get(seat) == null : seat == this.turn;
    }

    @Override
    public void play(int seat, JsonObject move) {
        if (this.finished) throw new HallException("finished");

        if (this.placing) {
            if (this.fleets.get(seat) != null) throw new HallException("not_your_turn");

            if (move != null && move.has("random") && move.get("random").getAsBoolean()) {
                this.fleets.set(seat, randomFleet(new Random()));
            } else {
                this.fleets.set(seat, parseFleet(move));
            }

            this.afterPlacing();
            return;
        }

        this.requireTurn(seat);
        this.shoot(seat, intOf(move, "x"), intOf(move, "y"));
    }

    private void afterPlacing() {
        if (this.fleets.get(0) != null && this.fleets.get(1) != null) {
            this.placing = false;
            this.turn = 0;
        }
    }

    static List<Ship> parseFleet(JsonObject move) {
        if (move == null || !move.has("ships") || !move.get("ships").isJsonArray())
            throw new HallException("bad_fleet");

        List<Ship> ships = new ArrayList<>();

        for (JsonElement element : move.getAsJsonArray("ships")) {
            JsonObject ship = element.getAsJsonObject();

            ships.add(new Ship(
                    intOf(ship, "x"),
                    intOf(ship, "y"),
                    intOf(ship, "length"),
                    ship.has("vertical") && ship.get("vertical").getAsBoolean()));
        }

        if (!valid(ships)) throw new HallException("bad_fleet");

        return ships;
    }

    /** The whole fleet, each ship in the sea, none touching another (not even by a corner). */
    static boolean valid(List<Ship> ships) {
        List<Integer> lengths = new ArrayList<>();

        for (Ship ship : ships) lengths.add(ship.length());

        List<Integer> wanted = new ArrayList<>();

        for (int length : FLEET) wanted.add(length);

        lengths.sort(null);
        wanted.sort(null);

        return lengths.equals(wanted) && fits(ships);
    }

    /** Each ship in the sea, none touching another (not even by a corner). */
    static boolean fits(List<Ship> ships) {
        int[][] taken = new int[SIZE][SIZE];

        for (int index = 0; index < ships.size(); index++) {
            Ship ship = ships.get(index);

            for (int i = 0; i < ship.length(); i++) {
                int x = ship.x() + (ship.vertical() ? 0 : i);
                int y = ship.y() + (ship.vertical() ? i : 0);

                if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return false;

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;

                        if (nx >= 0
                                && ny >= 0
                                && nx < SIZE
                                && ny < SIZE
                                && taken[ny][nx] != 0
                                && taken[ny][nx] != index + 1) return false;
                    }
                }

                taken[y][x] = index + 1;
            }
        }

        return true;
    }

    static List<Ship> randomFleet(Random random) {
        while (true) {
            List<Ship> ships = new ArrayList<>();
            boolean failed = false;

            for (int length : FLEET) {
                boolean placed = false;

                for (int attempt = 0; attempt < 200 && !placed; attempt++) {
                    boolean vertical = random.nextBoolean();
                    int x = random.nextInt(vertical ? SIZE : SIZE - length + 1);
                    int y = random.nextInt(vertical ? SIZE - length + 1 : SIZE);

                    ships.add(new Ship(x, y, length, vertical));

                    if (fits(ships)) {
                        placed = true;
                    } else {
                        ships.remove(ships.size() - 1);
                    }
                }

                if (!placed) {
                    failed = true;
                    break;
                }
            }

            if (!failed) return ships;
        }
    }

    private void shoot(int seat, int x, int y) {
        int target = 1 - seat;

        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE || this.shots[target][y][x] != 0)
            throw new HallException("bad_move");

        Ship hit = this.shipAt(target, x, y);

        this.shots[target][y][x] = hit == null ? 1 : 2;
        this.lastX = x;
        this.lastY = y;
        this.lastSeat = seat;
        this.moves++;

        if (hit == null) {
            this.lastResult = "miss";
            this.turn = target;
            return;
        }

        this.lastResult = this.sunk(target, hit) ? "sink" : "hit";

        if (this.fleets.get(target).stream().allMatch(ship -> this.sunk(target, ship))) this.win(seat);
    }

    private Ship shipAt(int seat, int x, int y) {
        for (Ship ship : this.fleets.get(seat)) {
            if (ship.covers(x, y)) return ship;
        }

        return null;
    }

    private boolean sunk(int seat, Ship ship) {
        for (int i = 0; i < ship.length(); i++) {
            int x = ship.x() + (ship.vertical() ? 0 : i);
            int y = ship.y() + (ship.vertical() ? i : 0);

            if (this.shots[seat][y][x] != 2) return false;
        }

        return true;
    }

    /** The robot: places at random; shoots round a hit ship until it sinks, otherwise one cell in two at random. */
    @Override
    public void bot(int seat, Random random) {
        if (this.placing) {
            this.fleets.set(seat, randomFleet(random));
            this.afterPlacing();
            return;
        }

        int target = 1 - seat;
        List<int[]> wounded = new ArrayList<>();

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (this.shots[target][y][x] == 2 && !this.sunk(target, this.shipAt(target, x, y)))
                    wounded.add(new int[] {x, y});
            }
        }

        List<int[]> choices = new ArrayList<>();

        if (!wounded.isEmpty()) {
            boolean lined = wounded.size() > 1;
            boolean vertical = lined && wounded.get(0)[0] == wounded.get(1)[0];

            for (int[] cell : wounded) {
                int[][] around = lined
                        ? (vertical ? new int[][] {{0, -1}, {0, 1}} : new int[][] {{-1, 0}, {1, 0}})
                        : new int[][] {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

                for (int[] d : around) {
                    int x = cell[0] + d[0];
                    int y = cell[1] + d[1];

                    if (x >= 0 && y >= 0 && x < SIZE && y < SIZE && this.shots[target][y][x] == 0)
                        choices.add(new int[] {x, y});
                }
            }
        }

        if (choices.isEmpty()) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (this.shots[target][y][x] == 0 && (x + y) % 2 == 0 && !this.besideSunk(target, x, y))
                        choices.add(new int[] {x, y});
                }
            }
        }

        if (choices.isEmpty()) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    if (this.shots[target][y][x] == 0) choices.add(new int[] {x, y});
                }
            }
        }

        int[] cell = choices.get(random.nextInt(choices.size()));

        this.shoot(seat, cell[0], cell[1]);
    }

    /** No ship touches another: the cells round a sunk ship are water. */
    private boolean besideSunk(int seat, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;

                if (nx < 0 || ny < 0 || nx >= SIZE || ny >= SIZE || this.shots[seat][ny][nx] != 2) continue;

                if (this.sunk(seat, this.shipAt(seat, nx, ny))) return true;
            }
        }

        return false;
    }

    @Override
    public JsonObject view(int seat) {
        JsonObject view = this.base(seat);

        view.addProperty("phase", this.placing ? "placing" : "shooting");
        view.addProperty("size", SIZE);

        JsonArray fleet = new JsonArray();

        for (int length : FLEET) fleet.add(length);

        view.add("fleet", fleet);

        JsonArray seas = new JsonArray();

        for (int owner = 0; owner < 2; owner++) {
            JsonObject sea = new JsonObject();
            StringBuilder cells = new StringBuilder();

            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int shot = this.shots[owner][y][x];
                    Ship ship = this.fleets.get(owner) == null ? null : this.shipAt(owner, x, y);

                    cells.append(shot == 1 ? 'o' : shot == 2 ? (this.sunk(owner, ship) ? '#' : 'x') : '.');
                }
            }

            sea.addProperty("shots", cells.toString());
            sea.addProperty("ready", this.fleets.get(owner) != null);

            JsonArray ships = new JsonArray();
            List<Ship> owned = this.fleets.get(owner);

            if (owned != null) {
                for (Ship ship : owned) {
                    if (owner != seat && !this.finished && !this.sunk(owner, ship)) continue;

                    JsonObject entry = new JsonObject();

                    entry.addProperty("x", ship.x());
                    entry.addProperty("y", ship.y());
                    entry.addProperty("length", ship.length());
                    entry.addProperty("vertical", ship.vertical());
                    entry.addProperty("sunk", this.sunk(owner, ship));
                    ships.add(entry);
                }
            }

            sea.add("ships", ships);

            int left = 0;

            if (owned != null) {
                for (Ship ship : owned) {
                    if (!this.sunk(owner, ship)) left++;
                }
            }

            sea.addProperty("left", left);
            seas.add(sea);
        }

        view.add("seas", seas);
        view.addProperty("lastX", this.lastX);
        view.addProperty("lastY", this.lastY);
        view.addProperty("lastSeat", this.lastSeat);
        view.addProperty("lastResult", this.lastResult);
        return view;
    }

    boolean placing() {
        return this.placing;
    }
}
