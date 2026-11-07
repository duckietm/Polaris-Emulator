package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Four in a row: a board of 7 columns and 6 rows stood up; each player drops a disc into a column in turn (red, seat
 * 0, begins). The first to line up four discs, straight or across, wins; a full board is a draw.
 */
public final class ConnectFour extends TurnGame {

    public static final int COLUMNS = 7;
    public static final int ROWS = 6;
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    private static final int DEPTH = 5;

    /** Row 0 is the top. */
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
        this.drop(seat, intOf(move, "x"));
    }

    void drop(int seat, int column) {
        int row = this.landing(column);

        if (row < 0) throw new HallException("bad_move");

        this.board[row][column] = seat + 1;
        this.lastX = column;
        this.lastY = row;

        List<int[]> won = this.lineThrough(column, row, seat + 1);

        if (won != null) {
            this.line = won;
            this.moves++;
            this.win(seat);
            return;
        }

        this.next();

        if (this.moves >= ROWS * COLUMNS) this.draw();
    }

    private int landing(int column) {
        if (column < 0 || column >= COLUMNS) return -1;

        for (int row = ROWS - 1; row >= 0; row--) {
            if (this.board[row][column] == 0) return row;
        }

        return -1;
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

            if (cells.size() >= 4) return cells;
        }

        return null;
    }

    private int at(int x, int y) {
        return x < 0 || y < 0 || x >= COLUMNS || y >= ROWS ? -1 : this.board[y][x];
    }

    /** The robot: a short look ahead (negamax), the middle columns first; now and then a plainer move. */
    @Override
    public void bot(int seat, Random random) {
        int[] order = {3, 2, 4, 1, 5, 0, 6};
        int best = Integer.MIN_VALUE;
        List<Integer> choices = new ArrayList<>();
        int depth = random.nextInt(5) == 0 ? 2 : DEPTH;

        for (int column : order) {
            int row = this.landing(column);

            if (row < 0) continue;

            this.board[row][column] = seat + 1;

            int score = this.lineThrough(column, row, seat + 1) != null
                    ? 100_000
                    : -this.search(1 - seat, depth - 1, -1_000_000, 1_000_000);

            this.board[row][column] = 0;

            if (score > best) {
                best = score;
                choices.clear();
            }

            if (score == best) choices.add(column);
        }

        if (choices.isEmpty()) throw new HallException("bad_move");

        this.drop(seat, choices.get(random.nextInt(choices.size())));
    }

    private int search(int seat, int depth, int alpha, int beta) {
        boolean any = false;
        int[] order = {3, 2, 4, 1, 5, 0, 6};

        for (int column : order) {
            int row = this.landing(column);

            if (row < 0) continue;

            this.board[row][column] = seat + 1;
            boolean wins = this.lineThrough(column, row, seat + 1) != null;
            this.board[row][column] = 0;

            if (wins) return 50_000 + depth;

            any = true;
        }

        if (!any) return 0;
        if (depth <= 0) return this.score(seat + 1) - this.score(2 - seat);

        int best = -1_000_000;

        for (int column : order) {
            int row = this.landing(column);

            if (row < 0) continue;

            this.board[row][column] = seat + 1;
            int value = -this.search(1 - seat, depth - 1, -beta, -alpha);
            this.board[row][column] = 0;

            best = Math.max(best, value);
            alpha = Math.max(alpha, value);

            if (alpha >= beta) break;
        }

        return best;
    }

    /** How good the board looks for these discs: the middle column, and windows of four with no other disc. */
    private int score(int mark) {
        int total = 0;

        for (int y = 0; y < ROWS; y++) {
            if (this.board[y][3] == mark) total += 3;
        }

        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLUMNS; x++) {
                for (int[] d : DIRECTIONS) {
                    int own = 0;
                    int empty = 0;

                    for (int i = 0; i < 4; i++) {
                        int cell = this.at(x + d[0] * i, y + d[1] * i);

                        if (cell == mark) own++;
                        else if (cell == 0) empty++;
                    }

                    if (own + empty < 4) continue;
                    if (own == 3) total += 5;
                    else if (own == 2) total += 2;
                }
            }
        }

        return total;
    }

    @Override
    public JsonObject view(int seat) {
        JsonObject view = this.base(seat);
        StringBuilder cells = new StringBuilder();

        for (int[] row : this.board) {
            for (int cell : row) cells.append(cell == 0 ? '.' : cell == 1 ? 'R' : 'Y');
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
}
