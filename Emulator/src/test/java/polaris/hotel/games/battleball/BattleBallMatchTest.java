package polaris.hotel.games.battleball;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BattleBallMatchTest {

    private static final long NOW = 1_000_000L;

    /** Two players, red (1) and blue (2), the game started (after the countdown), no power-up on the floor. */
    private static BattleBallMatch game(int arena, Set<Integer> powers) {
        BattleBallMatch match =
                new BattleBallMatch(1, new BattleBallRules(arena, 2, 3, powers, true, 3), new Random(3));

        match.join(1, "P1", "", "M", 0, NOW);
        match.join(2, "P2", "", "F", 1, NOW);
        match.start(1, NOW);
        match.advance(playing());
        match.powerUps().clear();
        return match;
    }

    private static long playing() {
        return NOW + BattleBallMatch.COUNTDOWN_MS;
    }

    /** Bounces a player to (x, y), a tile away, and lets the bounce happen. */
    private static long hop(BattleBallMatch match, int userId, int x, int y, long now) {
        match.walk(userId, x, y, now);
        now += BattleBallMatch.MOVE_MS + BattleBallMatch.STEP_MS;
        match.advance(now);
        return now;
    }

    @Test
    void aGameNeedsTwoTeams() {
        BattleBallMatch match = new BattleBallMatch(1, BattleBallRules.standard(), new Random(1));

        match.join(1, "P1", "", "M", 0, NOW);

        assertEquals(
                "not_enough_teams",
                assertThrows(BattleBallException.class, () -> match.start(1, NOW))
                        .code());

        match.addBot(1, 1, NOW);
        match.addBot(1, 1, NOW);
        match.addBot(1, 1, NOW);

        assertEquals(
                "team_sizes",
                assertThrows(BattleBallException.class, () -> match.start(1, NOW))
                        .code());
    }

    @Test
    void aTileTakesThreeHitsFromGrey() {
        BattleBallMatch match = game(1, Set.of());
        long now = playing();

        match.place(1, 10, 10, 2);
        now = hop(match, 1, 11, 10, now);

        assertEquals(BattleBallMatch.CLICKED, match.tileState(11, 10));
        assertEquals(0, match.tileColour(11, 10));

        now = hop(match, 1, 10, 10, now);
        now = hop(match, 1, 11, 10, now);
        assertEquals(BattleBallMatch.PRESSED, match.tileState(11, 10));

        now = hop(match, 1, 10, 10, now);
        hop(match, 1, 11, 10, now);
        assertEquals(BattleBallMatch.SEALED, match.tileState(11, 10));
        assertTrue(match.teamScores()[0] > 0);
    }

    @Test
    void barebonesClassicTakesFourHits() {
        BattleBallMatch match = game(5, Set.of(1, 2));
        long now = playing();

        assertTrue(match.rules().powers().isEmpty());

        match.place(1, 10, 10, 2);
        hop(match, 1, 11, 10, now);

        assertEquals(BattleBallMatch.TOUCHED, match.tileState(11, 10));
    }

    @Test
    void anotherTeamStealsATileThatIsNotSealed() {
        BattleBallMatch match = game(1, Set.of());
        long now = playing();

        match.place(1, 10, 10, 2);
        match.place(2, 12, 12, 2);
        now = hop(match, 1, 11, 10, now);
        now = hop(match, 1, 10, 10, now);

        int red = match.teamScores()[0];

        now = hop(match, 2, 11, 11, now);
        hop(match, 2, 11, 10, now);

        assertEquals(1, match.tileColour(11, 10));
        assertEquals(BattleBallMatch.CLICKED, match.tileState(11, 10));
        assertTrue(match.teamScores()[0] < red);
    }

    @Test
    void sealingARingSealsWhatItCloses() {
        BattleBallMatch match = game(1, Set.of(BattleBallMatch.SPRING));
        long now = playing();
        int[][] ring = {{11, 9}, {11, 10}, {11, 11}, {10, 11}, {9, 11}, {9, 10}, {9, 9}, {10, 9}};

        match.place(1, 9, 9, 2);
        match.dropPowerUp(BattleBallMatch.SPRING, 10, 9);

        // The spring: every tile bounced on is sealed at once.
        now = hop(match, 1, 10, 9, now);
        match.usePower(1, now);

        for (int[] tile : ring) now = hop(match, 1, tile[0], tile[1], now);

        assertEquals(BattleBallMatch.SEALED, match.tileState(10, 10));
        assertEquals(0, match.tileColour(10, 10));
    }

    @Test
    void theLightBulbColoursAroundThePlayer() {
        BattleBallMatch match = game(1, Set.of(BattleBallMatch.LIGHT_BULB));
        long now = playing();

        match.place(1, 10, 10, 2);
        match.dropPowerUp(BattleBallMatch.LIGHT_BULB, 11, 10);
        now = hop(match, 1, 11, 10, now);

        assertEquals(BattleBallMatch.LIGHT_BULB, match.player(1).held());
        assertEquals(
                "no_power",
                assertThrows(BattleBallException.class, () -> match.usePower(2, NOW + 6000))
                        .code());

        match.usePower(1, now);

        assertEquals(0, match.player(1).held());
        assertEquals(BattleBallMatch.CLICKED, match.tileState(12, 11));
        assertEquals(0, match.tileColour(13, 10));
    }

    @Test
    void aPinBurstsTheBall() {
        BattleBallMatch match = game(1, Set.of(BattleBallMatch.BOX_OF_PINS));
        long now = playing();

        match.place(1, 8, 10, 2);
        match.place(2, 14, 10, 2);
        match.dropPowerUp(BattleBallMatch.BOX_OF_PINS, 9, 10);
        now = hop(match, 1, 9, 10, now);

        // Six tiles ahead of the player is (15, 10): pins round it.
        match.usePower(1, now);

        assertFalse(match.pins().isEmpty());

        for (BattleBallMatch.Pin pin : match.pins()) {
            int x = pin.x() - 1;
            boolean pinned = match.pins().stream().anyMatch(other -> other.x() == x && other.y() == pin.y());

            if (pinned
                    || !match.arena().walkable(x, pin.y())
                    || match.arena().heightAt(x, pin.y()) != match.arena().heightAt(pin.x(), pin.y())) {
                continue;
            }

            match.place(2, x, pin.y(), 2);
            hop(match, 2, pin.x(), pin.y(), now);

            assertEquals(
                    BattleBallMatch.PlayerState.BALL_BROKEN, match.player(2).state());
            return;
        }
    }

    @Test
    void theBombClearsAndStuns() {
        BattleBallMatch match = game(1, Set.of(BattleBallMatch.BOMB));
        long now = playing();

        match.place(1, 10, 10, 2);
        match.place(2, 12, 10, 2);
        now = hop(match, 2, 12, 11, now);
        now = hop(match, 2, 11, 11, now);

        assertEquals(1, match.tileColour(11, 11));

        match.dropPowerUp(BattleBallMatch.BOMB, 10, 11);
        now = hop(match, 1, 10, 11, now);
        match.usePower(1, now);

        assertEquals(BattleBallMatch.DEFAULT, match.tileState(11, 11));
        assertEquals(BattleBallMatch.PlayerState.STUNNED, match.player(1).state());
        assertEquals(BattleBallMatch.PlayerState.STUNNED, match.player(2).state());
        assertEquals(
                "bad_tile",
                assertThrows(BattleBallException.class, () -> match.walk(1, -3, -3, NOW + 6000))
                        .code());
    }

    @Test
    void robotsPlayAGameToTheEndOnEveryArena() {
        for (int arena = 1; arena <= 5; arena++) {
            BattleBallMatch match = new BattleBallMatch(
                    1, new BattleBallRules(arena, 4, 3, Set.of(1, 2, 3, 4, 5, 6, 7, 8), true, 2), new Random(arena));

            match.join(1, "P1", "", "M", 0, NOW);
            match.fillWithBots(1, NOW);
            match.start(1, NOW);

            long now = NOW;

            for (int second = 0; second < 200 && match.state() != BattleBallMatch.State.FINISHED; second++) {
                now += 1000L;
                match.advance(now);
            }

            assertEquals(BattleBallMatch.State.FINISHED, match.state(), "arena " + arena);
            assertFalse(match.winners().isEmpty(), "arena " + arena);
            assertTrue(match.teamScores()[1] > 0, "arena " + arena);
            assertTrue(match.takeRecord());
        }
    }

    @Test
    void theViewShowsTheArena() {
        BattleBallMatch match = game(2, Set.of(1));
        JsonObject view = BattleBallViews.match(match, 1, playing());

        assertEquals(2, view.getAsJsonArray("players").size());
        assertEquals(
                match.arena().width() * match.arena().height(),
                view.get("tiles").getAsString().length());
        assertEquals("playing", view.get("state").getAsString());
    }
}
