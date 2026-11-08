package polaris.hotel.games.hall;

import java.util.List;

/** The games of the game hall: their names (in the API and the client), their seats, and a new game of each. */
public final class HallGames {

    /** A kind of game: how many may sit (from, to, by default). */
    public record Kind(String name, int minSeats, int maxSeats, int seats) {}

    public static final List<Kind> KINDS = List.of(
            new Kind("tictactoe", 2, 2, 2),
            new Kind("connect4", 2, 2, 2),
            new Kind("battleships", 2, 2, 2),
            new Kind("chess", 2, 2, 2),
            new Kind("poker", 2, 4, 4),
            new Kind("snails", 2, 6, 6),
            new Kind("quiz", 2, 8, 6),
            new Kind("tetris", 2, 4, 2),
            new Kind("diving", 2, 8, 4),
            new Kind("kart", 2, 8, 4),
            new Kind("copywriter", 2, 8, 4));

    private HallGames() {}

    public static Kind kind(String name) {
        for (Kind kind : KINDS) {
            if (kind.name().equals(name)) return kind;
        }

        throw new HallException("no_game");
    }

    public static HallGame create(String name) {
        return switch (name) {
            case "tictactoe" -> new TicTacToe();
            case "connect4" -> new ConnectFour();
            case "battleships" -> new Battleships();
            case "chess" -> new Chess();
            case "poker" -> new Poker();
            case "snails" -> new SnailRace();
            case "quiz" -> new Quiz(QuizBank.questions());
            case "tetris" -> new TetrisDuel();
            case "diving" -> new Diving();
            case "kart" -> new KartRace();
            case "copywriter" -> new CopyWriter();
            default -> throw new HallException("no_game");
        };
    }
}
