package polaris.hotel.games.hall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Chess, the Cunning Fox Gamehall's game (2004): the whole rules, white (seat 0) at the bottom and first. Castling,
 * taking en passant, promotion (a queen unless the player chooses), check; the game ends on checkmate, or a draw on
 * stalemate, fifty moves without a capture or a pawn move, three times the same position, or too few pieces left.
 *
 * <p>Squares are 0 to 63, row by row from the top (black's side): square = y * 8 + x. Pieces: 1 pawn, 2 knight,
 * 3 bishop, 4 rook, 5 queen, 6 king; white positive, black negative.
 */
public final class Chess extends TurnGame {

    static final int PAWN = 1;
    static final int KNIGHT = 2;
    static final int BISHOP = 3;
    static final int ROOK = 4;
    static final int QUEEN = 5;
    static final int KING = 6;
    private static final String LETTERS = ".pnbrqk";
    private static final int[] VALUES = {0, 100, 300, 320, 500, 900, 0};
    private static final int[][] KNIGHT_STEPS = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}
    };
    private static final int[][] KING_STEPS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] STRAIGHT = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** A move: from, to, the piece a pawn becomes (0 none), and what kind (castling, en passant). */
    record Move(int from, int to, int promotion, boolean castle, boolean enPassant) {}

    private int[] board = new int[64];
    /** Castling still allowed: white king side, white queen side, black king side, black queen side. */
    private boolean[] castling = new boolean[4];

    private int enPassant = -1;
    private int quiet;
    private final Map<String, Integer> seen = new HashMap<>();
    private int lastFrom = -1;
    private int lastTo = -1;
    private String ending = "";
    private final List<Integer> taken = new ArrayList<>();

    @Override
    public void start(int seats, Random random) {
        super.start(seats, random);
        this.board = new int[64];

        int[] back = {ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK};

        for (int x = 0; x < 8; x++) {
            this.board[x] = -back[x];
            this.board[8 + x] = -PAWN;
            this.board[48 + x] = PAWN;
            this.board[56 + x] = back[x];
        }

        this.castling = new boolean[] {true, true, true, true};
        this.enPassant = -1;
        this.quiet = 0;
        this.seen.clear();
        this.seen.put(this.key(), 1);
        this.lastFrom = -1;
        this.lastTo = -1;
        this.ending = "";
        this.taken.clear();
    }

    /** Sets a position for the tests: 64 letters (PNBRQK white, pnbrqk black, '.' empty), the side to move. */
    void setUp(String position, int side) {
        for (int i = 0; i < 64; i++) {
            char c = position.charAt(i);
            int piece = LETTERS.indexOf(Character.toLowerCase(c));

            this.board[i] = c == '.' ? 0 : Character.isUpperCase(c) ? piece : -piece;
        }

        this.castling = new boolean[4];
        this.turn = side;
        this.finished = false;
        this.seen.clear();
    }

    private static int colour(int seat) {
        return seat == 0 ? 1 : -1;
    }

    @Override
    public void play(int seat, JsonObject move) {
        this.requireTurn(seat);

        int from = intOf(move, "from");
        int to = intOf(move, "to");
        int promotion = Math.max(0, intOf(move, "promotion"));

        for (Move legal : this.legalMoves(seat)) {
            if (legal.from() == from
                    && legal.to() == to
                    && (legal.promotion() == 0 || legal.promotion() == (promotion == 0 ? QUEEN : promotion))) {
                this.apply(seat, legal);
                return;
            }
        }

        throw new HallException("bad_move");
    }

    private void apply(int seat, Move move) {
        int piece = this.board[move.to()];
        boolean capture = piece != 0 || move.enPassant();

        if (piece != 0) this.taken.add(piece);
        if (move.enPassant()) this.taken.add(-colour(seat) * PAWN);

        boolean pawn = Math.abs(this.board[move.from()]) == PAWN;

        this.make(move);
        this.lastFrom = move.from();
        this.lastTo = move.to();
        this.quiet = capture || pawn ? 0 : this.quiet + 1;
        this.next();

        String key = this.key();
        int times = this.seen.merge(key, 1, Integer::sum);

        if (this.legalMoves(this.turn).isEmpty()) {
            if (this.inCheck(this.turn)) {
                this.ending = "checkmate";
                this.win(seat);
            } else {
                this.ending = "stalemate";
                this.draw();
            }
        } else if (this.quiet >= 100) {
            this.ending = "fifty";
            this.draw();
        } else if (times >= 3) {
            this.ending = "repetition";
            this.draw();
        } else if (this.tooFewPieces()) {
            this.ending = "material";
            this.draw();
        }
    }

    /** Plays a move on the board (no checks), keeping castling and en passant right. */
    private int[] make(Move move) {
        int[] undo = new int[] {
            this.board[move.from()],
            this.board[move.to()],
            this.enPassant,
            this.castling[0] ? 1 : 0,
            this.castling[1] ? 1 : 0,
            this.castling[2] ? 1 : 0,
            this.castling[3] ? 1 : 0
        };
        int piece = this.board[move.from()];
        int side = Integer.signum(piece);

        this.board[move.to()] = move.promotion() != 0 ? side * move.promotion() : piece;
        this.board[move.from()] = 0;

        if (move.enPassant()) this.board[move.to() + 8 * side] = 0;

        if (move.castle()) {
            boolean kingSide = move.to() % 8 == 6;
            int row = move.to() / 8 * 8;

            this.board[row + (kingSide ? 5 : 3)] = this.board[row + (kingSide ? 7 : 0)];
            this.board[row + (kingSide ? 7 : 0)] = 0;
        }

        this.enPassant =
                Math.abs(piece) == PAWN && Math.abs(move.to() - move.from()) == 16 ? (move.from() + move.to()) / 2 : -1;

        for (int square : new int[] {move.from(), move.to()}) {
            if (square == 60) this.castling[0] = this.castling[1] = false;
            if (square == 4) this.castling[2] = this.castling[3] = false;
            if (square == 63) this.castling[0] = false;
            if (square == 56) this.castling[1] = false;
            if (square == 7) this.castling[2] = false;
            if (square == 0) this.castling[3] = false;
        }

        return undo;
    }

    private void unmake(Move move, int[] undo) {
        int side = Integer.signum(undo[0]);

        this.board[move.from()] = undo[0];
        this.board[move.to()] = undo[1];

        if (move.enPassant()) this.board[move.to() + 8 * side] = -side * PAWN;

        if (move.castle()) {
            boolean kingSide = move.to() % 8 == 6;
            int row = move.to() / 8 * 8;

            this.board[row + (kingSide ? 7 : 0)] = this.board[row + (kingSide ? 5 : 3)];
            this.board[row + (kingSide ? 5 : 3)] = 0;
        }

        this.enPassant = undo[2];

        for (int i = 0; i < 4; i++) this.castling[i] = undo[3 + i] == 1;
    }

    List<Move> legalMoves(int seat) {
        List<Move> legal = new ArrayList<>();

        for (Move move : this.pseudoMoves(seat)) {
            int[] undo = this.make(move);

            if (!this.inCheck(seat)) legal.add(move);

            this.unmake(move, undo);
        }

        return legal;
    }

    private List<Move> pseudoMoves(int seat) {
        List<Move> moves = new ArrayList<>();
        int side = colour(seat);

        for (int from = 0; from < 64; from++) {
            int piece = this.board[from];

            if (piece == 0 || Integer.signum(piece) != side) continue;

            int x = from % 8;
            int y = from / 8;

            switch (Math.abs(piece)) {
                case PAWN -> this.pawnMoves(moves, from, x, y, side);
                case KNIGHT -> this.steps(moves, from, x, y, side, KNIGHT_STEPS);
                case BISHOP -> this.slides(moves, from, x, y, side, DIAGONAL);
                case ROOK -> this.slides(moves, from, x, y, side, STRAIGHT);
                case QUEEN -> {
                    this.slides(moves, from, x, y, side, STRAIGHT);
                    this.slides(moves, from, x, y, side, DIAGONAL);
                }
                case KING -> {
                    this.steps(moves, from, x, y, side, KING_STEPS);
                    this.castles(moves, from, seat);
                }
                default -> {}
            }
        }

        return moves;
    }

    private void pawnMoves(List<Move> moves, int from, int x, int y, int side) {
        int dy = -side;
        int ny = y + dy;
        boolean promotes = ny == 0 || ny == 7;

        if (ny < 0 || ny > 7) return;

        if (this.board[ny * 8 + x] == 0) {
            this.pawnTo(moves, from, ny * 8 + x, promotes, false);

            int startRow = side == 1 ? 6 : 1;

            if (y == startRow && this.board[(y + 2 * dy) * 8 + x] == 0)
                moves.add(new Move(from, (y + 2 * dy) * 8 + x, 0, false, false));
        }

        for (int dx = -1; dx <= 1; dx += 2) {
            int nx = x + dx;

            if (nx < 0 || nx > 7) continue;

            int to = ny * 8 + nx;

            if (this.board[to] != 0 && Integer.signum(this.board[to]) == -side) {
                this.pawnTo(moves, from, to, promotes, false);
            } else if (to == this.enPassant) {
                moves.add(new Move(from, to, 0, false, true));
            }
        }
    }

    private void pawnTo(List<Move> moves, int from, int to, boolean promotes, boolean enPassant) {
        if (!promotes) {
            moves.add(new Move(from, to, 0, false, enPassant));
            return;
        }

        for (int piece : new int[] {QUEEN, ROOK, BISHOP, KNIGHT}) moves.add(new Move(from, to, piece, false, false));
    }

    private void steps(List<Move> moves, int from, int x, int y, int side, int[][] steps) {
        for (int[] step : steps) {
            int nx = x + step[0];
            int ny = y + step[1];

            if (nx < 0 || ny < 0 || nx > 7 || ny > 7) continue;

            int to = ny * 8 + nx;

            if (Integer.signum(this.board[to]) != side) moves.add(new Move(from, to, 0, false, false));
        }
    }

    private void slides(List<Move> moves, int from, int x, int y, int side, int[][] lines) {
        for (int[] line : lines) {
            int nx = x + line[0];
            int ny = y + line[1];

            while (nx >= 0 && ny >= 0 && nx <= 7 && ny <= 7) {
                int to = ny * 8 + nx;

                if (this.board[to] != 0) {
                    if (Integer.signum(this.board[to]) != side) moves.add(new Move(from, to, 0, false, false));
                    break;
                }

                moves.add(new Move(from, to, 0, false, false));
                nx += line[0];
                ny += line[1];
            }
        }
    }

    private void castles(List<Move> moves, int from, int seat) {
        int row = seat == 0 ? 56 : 0;

        if (from != row + 4 || this.inCheck(seat)) return;

        int rook = colour(seat) * ROOK;

        if (this.castling[seat * 2]
                && this.board[row + 7] == rook
                && this.board[row + 5] == 0
                && this.board[row + 6] == 0
                && !this.attacked(row + 5, seat)
                && !this.attacked(row + 6, seat)) moves.add(new Move(from, row + 6, 0, true, false));

        if (this.castling[seat * 2 + 1]
                && this.board[row] == rook
                && this.board[row + 1] == 0
                && this.board[row + 2] == 0
                && this.board[row + 3] == 0
                && !this.attacked(row + 3, seat)
                && !this.attacked(row + 2, seat)) moves.add(new Move(from, row + 2, 0, true, false));
    }

    boolean inCheck(int seat) {
        for (int square = 0; square < 64; square++) {
            if (this.board[square] == colour(seat) * KING) return this.attacked(square, seat);
        }

        return false;
    }

    /** Whether the other side attacks this square (of this seat). */
    private boolean attacked(int square, int seat) {
        int enemy = -colour(seat);
        int x = square % 8;
        int y = square / 8;

        for (int[] step : KNIGHT_STEPS) {
            if (this.pieceAt(x + step[0], y + step[1]) == enemy * KNIGHT) return true;
        }

        for (int[] step : KING_STEPS) {
            if (this.pieceAt(x + step[0], y + step[1]) == enemy * KING) return true;
        }

        // Enemy pawns attack towards this side: a white pawn below the square, a black one above.
        int pawnRow = y + enemy;

        if (this.pieceAt(x - 1, pawnRow) == enemy * PAWN || this.pieceAt(x + 1, pawnRow) == enemy * PAWN) return true;

        return this.slidesTo(x, y, STRAIGHT, enemy, ROOK) || this.slidesTo(x, y, DIAGONAL, enemy, BISHOP);
    }

    private boolean slidesTo(int x, int y, int[][] lines, int enemy, int kind) {
        for (int[] line : lines) {
            int nx = x + line[0];
            int ny = y + line[1];

            while (nx >= 0 && ny >= 0 && nx <= 7 && ny <= 7) {
                int piece = this.board[ny * 8 + nx];

                if (piece != 0) {
                    if (piece == enemy * kind || piece == enemy * QUEEN) return true;
                    break;
                }

                nx += line[0];
                ny += line[1];
            }
        }

        return false;
    }

    private int pieceAt(int x, int y) {
        return x < 0 || y < 0 || x > 7 || y > 7 ? 0 : this.board[y * 8 + x];
    }

    private boolean tooFewPieces() {
        int minors = 0;

        for (int piece : this.board) {
            int kind = Math.abs(piece);

            if (kind == PAWN || kind == ROOK || kind == QUEEN) return false;
            if (kind == KNIGHT || kind == BISHOP) minors++;
        }

        return minors <= 1;
    }

    private String key() {
        StringBuilder key = new StringBuilder(70);

        for (int piece : this.board) key.append((char) ('g' + piece));

        key.append(this.turn).append(this.enPassant);

        for (boolean right : this.castling) key.append(right ? '1' : '0');

        return key.toString();
    }

    // ---- The robot: a short look ahead (alpha-beta), the material and the middle of the board

    @Override
    public void bot(int seat, Random random) {
        List<Move> moves = this.legalMoves(seat);

        if (moves.isEmpty()) throw new HallException("bad_move");

        int depth = random.nextInt(4) == 0 ? 1 : 2;
        int best = Integer.MIN_VALUE;
        List<Move> choices = new ArrayList<>();

        for (Move move : moves) {
            int[] undo = this.make(move);
            int value = -this.search(1 - seat, depth, -1_000_000, 1_000_000) + random.nextInt(12);

            this.unmake(move, undo);

            if (value > best) {
                best = value;
                choices.clear();
            }

            if (value == best) choices.add(move);
        }

        Move chosen = choices.get(random.nextInt(choices.size()));

        this.apply(seat, chosen);
    }

    private int search(int seat, int depth, int alpha, int beta) {
        if (depth <= 0) return this.evaluate(seat);

        List<Move> moves = this.legalMoves(seat);

        if (moves.isEmpty()) return this.inCheck(seat) ? -100_000 - depth : 0;

        int best = -1_000_000;

        for (Move move : moves) {
            int[] undo = this.make(move);
            int value = -this.search(1 - seat, depth - 1, -beta, -alpha);

            this.unmake(move, undo);
            best = Math.max(best, value);
            alpha = Math.max(alpha, value);

            if (alpha >= beta) break;
        }

        return best;
    }

    private int evaluate(int seat) {
        int total = 0;

        for (int square = 0; square < 64; square++) {
            int piece = this.board[square];

            if (piece == 0) continue;

            int x = square % 8;
            int y = square / 8;
            int centre = 6 - (Math.abs(2 * x - 7) + Math.abs(2 * y - 7)) / 2;
            int kind = Math.abs(piece);
            int value = VALUES[kind] + (kind == KING ? 0 : centre * 3);

            if (kind == PAWN) value += (piece > 0 ? 6 - y : y - 1) * 6;

            total += Integer.signum(piece) * value;
        }

        return colour(seat) * total;
    }

    @Override
    public void forfeit(int seat) {
        if (!this.finished) this.ending = "resign";

        super.forfeit(seat);
    }

    @Override
    public JsonObject view(int seat) {
        JsonObject view = this.base(seat);
        StringBuilder cells = new StringBuilder(64);

        for (int piece : this.board) {
            char letter = LETTERS.charAt(Math.abs(piece));

            cells.append(piece > 0 ? Character.toUpperCase(letter) : letter);
        }

        view.addProperty("board", cells.toString());
        view.addProperty("check", !this.finished && this.inCheck(this.turn));
        view.addProperty("lastFrom", this.lastFrom);
        view.addProperty("lastTo", this.lastTo);
        view.addProperty("ending", this.ending);

        JsonArray captured = new JsonArray();

        for (int piece : this.taken) {
            char letter = LETTERS.charAt(Math.abs(piece));

            captured.add(String.valueOf(piece > 0 ? Character.toUpperCase(letter) : letter));
        }

        view.add("taken", captured);

        JsonArray legal = new JsonArray();

        if (!this.finished && seat == this.turn) {
            for (Move move : this.legalMoves(seat)) {
                if (move.promotion() == 0 || move.promotion() == QUEEN) legal.add(move.from() * 64 + move.to());
            }
        }

        view.add("legal", legal);
        return view;
    }

    int at(int square) {
        return this.board[square];
    }
}
