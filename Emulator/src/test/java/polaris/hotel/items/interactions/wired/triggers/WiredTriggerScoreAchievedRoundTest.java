package polaris.hotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.games.Game;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;

/** AIR's Score Achieved fires once per game round; a new start or a changed save arms it again. */
class WiredTriggerScoreAchievedRoundTest {
    @Test
    void firesOncePerRound() {
        WiredTriggerScoreAchieved box = new WiredTriggerScoreAchieved(1, 1, mock(Item.class), "", 0, 0);
        box.saveData(new WiredSettings(new int[] {7, 0}, "", new int[0], 0));
        Game game = mock(Game.class);
        when(game.getStartTime()).thenReturn(100);

        assertTrue(box.spendRound(game));
        assertFalse(box.spendRound(game));

        when(game.getStartTime()).thenReturn(160);
        assertTrue(box.spendRound(game));

        // The same values saved again keep it spent; another team arms it.
        box.saveData(new WiredSettings(new int[] {7, 0}, "", new int[0], 0));
        assertFalse(box.spendRound(game));
        box.saveData(new WiredSettings(new int[] {7, 1}, "", new int[0], 0));
        assertTrue(box.spendRound(game));
    }
}
