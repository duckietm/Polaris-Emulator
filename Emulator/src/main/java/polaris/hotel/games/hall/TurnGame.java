package polaris.hotel.games.hall;

import com.google.gson.JsonObject;
import java.util.List;

/** A game of two seats playing in turns: whose turn it is, the end, the winner, a forfeit. */
abstract class TurnGame implements HallGame {

    protected int turn;
    protected boolean finished;
    protected List<Integer> winners = List.of();
    protected int moves;

    @Override
    public void start(int seats, java.util.Random random) {
        this.turn = 0;
        this.finished = false;
        this.winners = List.of();
        this.moves = 0;
    }

    @Override
    public boolean waitingFor(int seat) {
        return !this.finished && seat == this.turn;
    }

    @Override
    public void forfeit(int seat) {
        if (this.finished) return;

        this.finished = true;
        this.winners = List.of(1 - seat);
    }

    @Override
    public boolean finished() {
        return this.finished;
    }

    @Override
    public List<Integer> winners() {
        return this.winners;
    }

    protected void requireTurn(int seat) {
        if (this.finished) throw new HallException("finished");
        if (seat != this.turn) throw new HallException("not_your_turn");
    }

    protected void win(int seat) {
        this.finished = true;
        this.winners = List.of(seat);
    }

    protected void draw() {
        this.finished = true;
        this.winners = List.of();
    }

    protected void next() {
        this.turn = 1 - this.turn;
        this.moves++;
    }

    protected JsonObject base(int seat) {
        JsonObject view = new JsonObject();

        view.addProperty("turn", this.finished ? -1 : this.turn);
        view.addProperty("moves", this.moves);
        return view;
    }

    static int intOf(JsonObject move, String key) {
        try {
            return move != null && move.has(key) ? move.get(key).getAsInt() : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
