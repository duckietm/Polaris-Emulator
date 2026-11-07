package polaris.hotel.games.hall;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Random;

/**
 * A game of the game hall (the Cunning Fox Gamehall of Habbo's 2004 hotel, and Habbi's additions): the rules only,
 * seat by seat (0 to n - 1). The table ({@link HallTable}) keeps the players, the clock and the robots. Not
 * thread-safe: the manager locks the table.
 */
public interface HallGame {

    /** Starts a game for this many seats. */
    void start(int seats, Random random);

    /** A seat's move; refused with a {@link HallException}. */
    void play(int seat, JsonObject move);

    /** Whether this seat has to act now (its turn, or a step everybody does at once). */
    boolean waitingFor(int seat);

    /** A move for this seat, as a robot (or a player out of time) would play it. */
    void bot(int seat, Random random);

    /** The time goes by, for the games that run on their own (a race): robots included. */
    default void tick(long now, Random random) {}

    /** This seat gives up (left, or out of time too often). */
    void forfeit(int seat);

    boolean finished();

    /** The winning seats (empty: a draw). */
    List<Integer> winners();

    /** What this seat sees (-1: somebody watching). */
    JsonObject view(int seat);
}
