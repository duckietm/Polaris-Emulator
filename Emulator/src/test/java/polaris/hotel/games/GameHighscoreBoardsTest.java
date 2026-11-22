package polaris.hotel.games;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import polaris.hotel.wired.highscores.WiredHighscoreScoreType;

/** What a round writes into a high score board, by the board's kind. */
class GameHighscoreBoardsTest {
    @Test
    void timeBoardsStoreTheLengthOfTheRoundForEveryTeam() {
        for (WiredHighscoreScoreType kind :
                new WiredHighscoreScoreType[] {WiredHighscoreScoreType.FASTESTTIME, WiredHighscoreScoreType.LONGESTTIME
                }) {
            assertEquals(42, Game.boardScore(kind, 900, 42));
            assertTrue(Game.writesBoard(kind, 0, false), "a team without points is timed too");
        }
    }

    @Test
    void pointsBoardsStoreThePointsOfATeamThatScoredOrSharesTheTop() {
        for (WiredHighscoreScoreType kind : new WiredHighscoreScoreType[] {
            WiredHighscoreScoreType.CLASSIC, WiredHighscoreScoreType.PERTEAM, WiredHighscoreScoreType.MOSTWIN
        }) {
            assertEquals(900, Game.boardScore(kind, 900, 42));
            assertTrue(Game.writesBoard(kind, 900, false));
            assertTrue(Game.writesBoard(kind, 900, true));
            assertFalse(Game.writesBoard(kind, 0, false));
        }
    }
}
