package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Habbo's TicTacToe (the Cunning Fox Gamehall, 2004): five in a row on a board of 24 columns and 23 rows, X (seat 0)
 * begins, O follows. The first to line up five of their marks, straight or across, wins.
 */
public final class TicTacToe extends TurnGame {

    public static final int COLUMNS = 24;
    public static final int ROWS = 23;
    static final int IN_ROW = 5;
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    private final int[][] board = new int[ROWS][COLUMNS];
    private int lastX = -1;
    private int lastY = -1;
    private List<int[]> line = List.of();

    @Override
    public void start(int seats, Random random) {
        super.start(seats, random);

        for (int[] row : this.board) java.util.Arrays.fill(row, 0);

        this.lastX = -1;
        this.lastY = -1;
        this.line = List.of();
    }

    @Override
    public void play(int seat, JsonObject move) {
        this.requireTurn(seat);
        this.place(seat, intOf(move, "x"), intOf(move, "y"));
    }

    void place(int seat, int x, int y) {
        if (x < 0 || y < 0 || x >= COLUMNS || y >= ROWS || this.board[y][x] != 0) throw new HallException("bad_move");

        this.board[y][x] = seat + 1;
        this.lastX = x;
        this.lastY = y;

        List<int[]> won = this.lineThrough(x, y, seat + 1);

        if (won != null) {
            this.line = won;
            this.moves++;
            this.win(seat);
            return;
        }

        this.next();

        if (this.moves >= ROWS * COLUMNS) this.draw();
    }

    private List<int[]> lineThrough(int x, int y, int mark) {
        for (int[] d : DIRECTIONS) {
            List<int[]> cells = new ArrayList<>();

            cells.add(new int[] {x, y});

            for (int sign = -1; sign <= 1; sign += 2) {
                int cx = x + d[0] * sign;
                int cy = y + d[1] * sign;

                while (this.at(cx, cy) == mark) {
                    cells.add(new int[] {cx, cy});
                    cx += d[0] * sign;
                    cy += d[1] * sign;
                }
            }

            if (cells.size() >= IN_ROW) return cells;
        }

        return null;
    }

    private int at(int x, int y) {
        return x < 0 || y < 0 || x >= COLUMNS || y >= ROWS ? -1 : this.board[y][x];
    }

    /** The robot: the cell that best lengthens its own lines and blocks the other's, near the stones. */
    @Override
    public void bot(int seat, Random random) {
        int mine = seat + 1;
        int theirs = 2 - seat;
        long best = -1;
        List<int[]> choices = new ArrayList<>();

        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLUMNS; x++) {
                if (this.board[y][x] != 0 || !this.near(x, y)) continue;

                long score = this.value(x, y, mine) * 11 / 10 + this.value(x, y, theirs);

                if (score > best) {
                    best = score;
                    choices.clear();
                }

                if (score == best) choices.add(new int[] {x, y});
            }
        }

        int[] cell =
                choices.isEmpty() ? new int[] {COLUMNS / 2, ROWS / 2} : choices.get(random.nextInt(choices.size()));

        if (this.board[cell[1]][cell[0]] != 0) cell = this.anyFree();

        this.place(seat, cell[0], cell[1]);
    }

    private boolean near(int x, int y) {
        if (this.moves == 0) return x == COLUMNS / 2 && y == ROWS / 2;

        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (this.at(x + dx, y + dy) > 0) return true;
            }
        }

        return false;
    }

    /** What a mark at (x, y) would be worth for these marks: longer open lines are worth much more. */
    private long value(int x, int y, int mark) {
        long total = 0;

        for (int[] d : DIRECTIONS) {
            int count = 1;
            int open = 0;

            for (int sign = -1; sign <= 1; sign += 2) {
                int cx = x + d[0] * sign;
                int cy = y + d[1] * sign;

                while (this.at(cx, cy) == mark) {
                    count++;
                    cx += d[0] * sign;
                    cy += d[1] * sign;
                }

                if (this.at(cx, cy) == 0) open++;
            }

            if (count >= IN_ROW) total += 1_000_000;
            else if (count == 4) total += open == 2 ? 100_000 : open == 1 ? 12_000 : 0;
            else if (count == 3) total += open == 2 ? 8_000 : open == 1 ? 500 : 0;
            else if (count == 2) total += open == 2 ? 300 : open == 1 ? 40 : 0;
            else total += open * 5;
        }

        return total;
    }

    private int[] anyFree() {
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLUMNS; x++) {
                if (this.board[y][x] == 0) return new int[] {x, y};
            }
        }

        throw new HallException("bad_move");
    }

    @Override
    public JsonObject view(int seat) {
        JsonObject view = this.base(seat);
        StringBuilder cells = new StringBuilder();

        for (int[] row : this.board) {
            for (int cell : row) cells.append(cell == 0 ? '.' : cell == 1 ? 'X' : 'O');
        }

        view.addProperty("board", cells.toString());
        view.addProperty("columns", COLUMNS);
        view.addProperty("rows", ROWS);
        view.addProperty("lastX", this.lastX);
        view.addProperty("lastY", this.lastY);

        JsonArray won = new JsonArray();

        for (int[] cell : this.line) won.add(cell[1] * COLUMNS + cell[0]);

        view.add("line", won);
        return view;
    }

    int cell(int x, int y) {
        return this.board[y][x];
    }
}
