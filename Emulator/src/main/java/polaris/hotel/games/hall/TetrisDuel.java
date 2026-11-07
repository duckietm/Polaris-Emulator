package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tetris in duel (2 to 4 players): each player plays their own board in their window (10 x 20) and tells the game
 * how it goes now and then (their board, their lines, their score). Lines cleared together send rubbish lines to the
 * others (2 lines: 1, 3 lines: 2, a Tetris: 4), shared out among them; the rubbish rises from the bottom of their
 * board. The last one standing wins; after {@link #MAX_MS}, the best score.
 *
 * <p>The game trusts what a window says (it is a friendly game): it only keeps the numbers within bounds. Robots play
 * a pretend game: their stack grows and shrinks at a steady pace, and rubbish makes it grow.
 */
public final class TetrisDuel implements HallGame {

    public static final int WIDTH = 10;
    public static final int HEIGHT = 20;
    static final long COUNTDOWN_MS = 3000L;
    static final long MAX_MS = 240_000L;
    private static final int[] RUBBISH = {0, 0, 1, 2, 4};

    static final class Board {
        String cells = ".".repeat(WIDTH * HEIGHT);
        int lines;
        int score;
        int level = 1;
        /** Rubbish sent to this board so far (the window adds what it has not added yet). */
        int received;

        boolean out;
        long outAt = -1;
        int place;
        // A robot's pretend stack.
        double stack;
    }

    private final List<Board> boards = new ArrayList<>();
    private Random random;
    private long startAt = -1;
    private long clock;
    private int outCount;
    private boolean finished;
    private List<Integer> winners = List.of();

    @Override
    public void start(int seats, Random random) {
        this.random = random;
        this.boards.clear();

        for (int seat = 0; seat < seats; seat++) this.boards.add(new Board());

        this.startAt = -1;
        this.outCount = 0;
        this.finished = false;
        this.winners = List.of();
    }

    @Override
    public void tick(long now, Random random) {
        this.clock = now;

        if (this.startAt < 0) this.startAt = now + COUNTDOWN_MS;

        if (!this.finished && now - this.startAt >= MAX_MS) this.endOnScore();
    }

    private boolean running() {
        return !this.finished && this.startAt >= 0 && this.clock >= this.startAt;
    }

    @Override
    public void play(int seat, JsonObject move) {
        if (!this.running()) throw new HallException("not_started");

        Board board = this.boards.get(seat);

        if (board.out) throw new HallException("finished");

        int lines = Math.max(0, Math.min(4, TurnGame.intOf(move, "cleared")));

        board.lines = Math.max(board.lines, Math.min(9999, TurnGame.intOf(move, "lines")));
        board.score = Math.max(board.score, Math.min(9_999_999, TurnGame.intOf(move, "score")));
        board.level = Math.max(1, Math.min(30, TurnGame.intOf(move, "level")));

        if (move != null && move.has("board")) {
            String cells = move.get("board").getAsString();

            if (cells.length() == WIDTH * HEIGHT && cells.matches("[.0-8]+")) board.cells = cells;
        }

        if (lines > 0) this.sendRubbish(seat, RUBBISH[lines]);

        if (move != null && move.has("out") && move.get("out").getAsBoolean()) this.knockOut(seat);
    }

    /** Rubbish lines go one by one to the other players still in, the one with the fewest received first. */
    private void sendRubbish(int from, int count) {
        for (int line = 0; line < count; line++) {
            Board target = null;

            for (int seat = 0; seat < this.boards.size(); seat++) {
                Board board = this.boards.get(seat);

                if (seat == from || board.out) continue;
                if (target == null || board.received < target.received) target = board;
            }

            if (target == null) return;

            target.received++;
            target.stack += 1;
        }
    }

    private void knockOut(int seat) {
        Board board = this.boards.get(seat);

        if (board.out) return;

        board.out = true;
        board.outAt = this.clock;
        board.place = this.boards.size() - this.outCount;
        this.outCount++;

        List<Integer> left = new ArrayList<>();

        for (int other = 0; other < this.boards.size(); other++) {
            if (!this.boards.get(other).out) left.add(other);
        }

        if (left.size() <= 1) {
            for (int other : left) this.boards.get(other).place = 1;

            this.winners = left;
            this.finished = true;
        }
    }

    private void endOnScore() {
        int best = -1;
        List<Integer> top = new ArrayList<>();

        for (int seat = 0; seat < this.boards.size(); seat++) {
            Board board = this.boards.get(seat);

            if (board.out) continue;

            if (board.score > best) {
                best = board.score;
                top.clear();
            }

            if (board.score == best) top.add(seat);
        }

        this.winners = top;
        this.finished = true;
    }

    /** Players send their board at least every couple of seconds; a robot plays a step of its pretend game. */
    @Override
    public boolean waitingFor(int seat) {
        return this.running() && seat < this.boards.size() && !this.boards.get(seat).out;
    }

    @Override
    public void bot(int seat, Random random) {
        Board board = this.boards.get(seat);

        if (board.out || !this.running()) return;

        // Every couple of seconds: a few pieces placed, now and then lines cleared.
        board.stack += 0.6 + random.nextDouble() * 0.8;

        int cleared = 0;

        if (board.stack > 4 && random.nextInt(100) < 55) {
            cleared = random.nextInt(100) < 8 ? 4 : 1 + random.nextInt(3);
            cleared = Math.min(cleared, (int) board.stack);
            board.stack -= cleared;
            board.lines += cleared;
            board.score += new int[] {0, 100, 300, 500, 800}[cleared] * board.level;
            board.level = 1 + board.lines / 10;
        }

        board.cells = this.pretendBoard(board, random);

        if (cleared > 0) this.sendRubbish(seat, RUBBISH[cleared]);
        if (board.stack >= HEIGHT) this.knockOut(seat);
    }

    private String pretendBoard(Board board, Random random) {
        StringBuilder cells = new StringBuilder();
        int height = (int) Math.min(HEIGHT, Math.round(board.stack));

        for (int y = 0; y < HEIGHT; y++) {
            int hole = random.nextInt(WIDTH);

            for (int x = 0; x < WIDTH; x++) {
                if (y < HEIGHT - height || x == hole) cells.append('.');
                else cells.append((char) ('1' + random.nextInt(7)));
            }
        }

        return cells.toString();
    }

    @Override
    public void forfeit(int seat) {
        if (!this.finished && seat < this.boards.size()) this.knockOut(seat);
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
        view.addProperty("startsIn", this.startAt < 0 ? COUNTDOWN_MS : Math.max(0L, this.startAt - this.clock));
        view.addProperty("timeLeft", this.startAt < 0 ? MAX_MS : Math.max(0L, this.startAt + MAX_MS - this.clock));

        JsonArray list = new JsonArray();

        for (Board board : this.boards) {
            JsonObject entry = new JsonObject();

            entry.addProperty("board", board.cells);
            entry.addProperty("lines", board.lines);
            entry.addProperty("score", board.score);
            entry.addProperty("level", board.level);
            entry.addProperty("received", board.received);
            entry.addProperty("out", board.out);
            entry.addProperty("place", board.place);
            list.add(entry);
        }

        view.add("boards", list);
        return view;
    }

    Board board(int seat) {
        return this.boards.get(seat);
    }
}
