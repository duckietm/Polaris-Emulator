package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.games.Game;
import org.junit.jupiter.api.Test;

/**
 * The counter kept one game at a time: with two games running in a room, a box scoring in one and then in the
 * other started the count over at every switch, so "times in game" never stopped it.
 */
class WiredScorePerGameRoundsTest {

    private static Game game(int startTime) {
        Game game = mock(Game.class);
        when(game.getStartTime()).thenReturn(startTime);
        return game;
    }

    @Test
    void twoRunningGamesKeepTheirOwnCounts() {
        WiredScorePerGame scored = new WiredScorePerGame();
        Game banzai = game(100);
        Game freeze = game(100);

        assertTrue(scored.tryCount(banzai, "red", 2));
        assertTrue(scored.tryCount(freeze, "red", 2));
        assertTrue(scored.tryCount(banzai, "red", 2));
        assertTrue(scored.tryCount(freeze, "red", 2));
        assertFalse(scored.tryCount(banzai, "red", 2), "the third score in the same round of banzai");
        assertFalse(scored.tryCount(freeze, "red", 2), "the third score in the same round of freeze");
    }

    @Test
    void aNewRoundOfTheSameGameStartsOver() {
        WiredScorePerGame scored = new WiredScorePerGame();
        Game game = game(100);

        assertTrue(scored.tryCount(game, "red", 1));
        assertFalse(scored.tryCount(game, "red", 1));

        when(game.getStartTime()).thenReturn(200);
        assertTrue(scored.tryCount(game, "red", 1));
    }

    @Test
    void unlimitedIsNeverCounted() {
        WiredScorePerGame scored = new WiredScorePerGame();
        Game game = game(100);

        for (int i = 0; i < 50; i++) {
            assertTrue(scored.tryCount(game, "red", WiredScorePerGame.UNLIMITED));
        }
    }
}
