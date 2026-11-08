package polaris.hotel.games.wobble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WobbleMatchTest {

    private static final long NOW = 1_000_000L;

    /** Two players (ids 1 and 2) on the beam, the round just started (after the countdown). */
    private static WobbleMatch duel(int roundsToWin, long seed) {
        WobbleMatch match = new WobbleMatch(1, new WobbleRules(roundsToWin, true, 2, false), new Random(seed));

        match.join(1, "P1", "", "M", NOW);
        match.join(2, "P2", "", "F", NOW);
        match.start(1, NOW);
        match.advance(NOW + WobbleMatch.COUNTDOWN_MS);
        return match;
    }

    private static long playing(WobbleMatch match) {
        return NOW + WobbleMatch.COUNTDOWN_MS;
    }

    /** Keeps a fighter upright, as a careful player would (leaning against the tilt, every step). */
    private static void balance(WobbleMatch match, int userId, long now) {
        WobbleMatch.Fighter fighter = match.onSide(match.side(userId));
        double coming = fighter.balance + fighter.speed * 5;

        match.lean(userId, coming > 10 ? -1 : coming < -10 ? 1 : 0, now);
    }

    @Test
    void aDuelNeedsTwoFighters() {
        WobbleMatch match = new WobbleMatch(1, WobbleRules.standard(), new Random(1));

        match.join(1, "P1", "", "M", NOW);

        assertEquals(
                "no_opponent",
                assertThrows(WobbleException.class, () -> match.start(1, NOW)).code());
        assertEquals(
                "not_host",
                assertThrows(WobbleException.class, () -> match.addBot(2, NOW)).code());

        match.addBot(1, NOW);
        match.start(1, NOW);

        assertEquals(WobbleMatch.State.COUNTDOWN, match.state());
        assertEquals(
                "not_playing",
                assertThrows(WobbleException.class, () -> match.push(1, 1, NOW)).code());
    }

    @Test
    void nobodyLeaningFallsInAFewSeconds() {
        WobbleMatch match = duel(1, 3);
        long now = playing(match);

        for (int step = 0; step < 400 && match.state() == WobbleMatch.State.PLAYING; step++) {
            now += WobbleMatch.STEP_MS;
            match.advance(now);
        }

        assertEquals(WobbleMatch.State.ROUND_OVER, match.state());
        assertTrue(now - playing(match) < 20_000L);
    }

    @Test
    void leaningAgainstTheTiltKeepsYouUp() {
        WobbleMatch match = duel(1, 4);
        long now = playing(match);

        for (int step = 0; step < 600; step++) {
            now += WobbleMatch.STEP_MS;
            balance(match, 1, now);
            balance(match, 2, now);
        }

        assertEquals(WobbleMatch.State.PLAYING, match.state());
        assertFalse(match.onSide(0).fell());
    }

    @Test
    void aPushOnlyReachesTheFighterNextToYou() {
        WobbleMatch match = duel(1, 5);
        long now = playing(match);

        match.push(1, 1, now);
        assertEquals("miss", match.onSide(0).action);

        // Walk up to the other fighter: they stand at 5, you at 2.
        match.move(1, 1, now + 400);
        match.move(1, 1, now + 800);

        assertEquals(4, match.onSide(0).pos());
        assertEquals(
                "blocked",
                assertThrows(WobbleException.class, () -> match.move(1, 1, NOW + 4200))
                        .code());

        double before = match.onSide(1).speed;

        match.push(1, -1, now + 900);

        assertEquals("hit", match.onSide(1).action);
        assertTrue(match.onSide(1).impulse < 0 || match.onSide(1).speed < before);
        assertEquals(
                "too_soon",
                assertThrows(WobbleException.class, () -> match.push(1, -1, now + 1000))
                        .code());
    }

    @Test
    void theBeamHasTwoEnds() {
        WobbleMatch match = duel(1, 6);
        long now = playing(match);

        match.move(1, -1, now + 400);
        match.move(1, -1, now + 800);

        assertEquals(0, match.onSide(0).pos());
        assertEquals(
                "beam_end",
                assertThrows(WobbleException.class, () -> match.move(1, -1, now + 1200))
                        .code());
    }

    @Test
    void stabilisingSetsYouStraightOnceARound() {
        WobbleMatch match = duel(1, 7);
        long now = playing(match);
        WobbleMatch.Fighter fighter = match.onSide(0);

        fighter.balance = 80;
        fighter.speed = 4;
        match.stabilise(1, now);

        assertTrue(fighter.stabilising(now));
        assertEquals(0.0, fighter.balance);
        assertEquals(0.0, fighter.speed);
        assertEquals(
                "already_stabilised",
                assertThrows(WobbleException.class, () -> match.stabilise(1, now + 5000))
                        .code());
    }

    @Test
    void theFirstToWinTheRoundsWinsTheMatch() {
        WobbleMatch match = duel(2, 8);
        long now = playing(match);

        for (int round = 0; round < 2; round++) {
            match.onSide(1).balance = 99;
            match.onSide(1).speed = 3;
            now += WobbleMatch.STEP_MS;
            match.advance(now);

            assertTrue(match.onSide(1).fell());
            assertEquals(WobbleMatch.State.ROUND_OVER, match.state());

            now += WobbleMatch.ROUND_OVER_MS + WobbleMatch.COUNTDOWN_MS + 100;
            match.advance(now);
        }

        assertEquals(WobbleMatch.State.FINISHED, match.state());
        assertEquals(0, match.winnerSide());
        assertEquals(2, match.onSide(0).wins());
        assertTrue(match.takeRecord());
        assertFalse(match.takeRecord());
    }

    @Test
    void leavingDuringTheMatchGivesItToTheOther() {
        WobbleMatch match = duel(2, 9);

        match.leave(2, playing(match));

        assertEquals(WobbleMatch.State.FINISHED, match.state());
        assertEquals(0, match.winnerSide());
    }

    @Test
    void suddenDeathComesAfterAWhile() {
        WobbleMatch match = duel(1, 10);
        long now = playing(match);

        for (int step = 0; step < 1200 && match.state() == WobbleMatch.State.PLAYING && !match.sudden(); step++) {
            now += WobbleMatch.STEP_MS;
            balance(match, 1, now);
            balance(match, 2, now);
        }

        assertTrue(match.sudden() || match.state() != WobbleMatch.State.PLAYING);
    }

    @Test
    void robotsPlayAMatchToTheEnd() {
        for (int level = 1; level <= 3; level++) {
            WobbleMatch match = new WobbleMatch(1, new WobbleRules(2, true, level, false), new Random(level));

            match.join(1, "P1", "", "M", NOW);
            match.addBot(1, NOW);
            match.start(1, NOW);

            long now = NOW;

            // The player only keeps their balance; the robot walks up and pushes.
            for (int step = 0; step < 40_000 && match.state() != WobbleMatch.State.FINISHED; step++) {
                now += WobbleMatch.STEP_MS;

                if (match.state() == WobbleMatch.State.PLAYING
                        && !match.onSide(0).fell()) balance(match, 1, now);
                else match.advance(now);
            }

            assertEquals(WobbleMatch.State.FINISHED, match.state(), "level " + level);
        }
    }

    @Test
    void theViewShowsBothFighters() {
        WobbleMatch match = duel(2, 11);
        JsonObject view = WobbleViews.match(match, 1, playing(match));

        assertEquals(2, view.getAsJsonArray("fighters").size());
        assertEquals(0, view.get("you").getAsInt());
        assertTrue(view.get("youAreHost").getAsBoolean());
        assertEquals("playing", view.get("state").getAsString());
    }
}
