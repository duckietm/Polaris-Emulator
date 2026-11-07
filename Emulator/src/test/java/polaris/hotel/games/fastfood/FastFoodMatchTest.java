package polaris.hotel.games.fastfood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class FastFoodMatchTest {

    private static final long NOW = 1_000_000L;
    private static final long STEP = FastFoodMatch.STEP_MS;

    /** Two players (1 and 2) at their tables, the game started (after the countdown). */
    private static FastFoodMatch game(int plates, boolean powers) {
        FastFoodMatch match = new FastFoodMatch(1, new FastFoodRules(2, plates, true, powers), new Random(5));

        match.join(1, "P1", "", "M", NOW);
        match.join(2, "P2", "", "F", NOW);
        match.start(1, NOW);
        match.advance(go());
        return match;
    }

    private static long go() {
        return NOW + FastFoodMatch.COUNTDOWN_MS;
    }

    private static FastFoodMatch.Plate plate(FastFoodMatch match, int userId) {
        return match.player(userId).plate();
    }

    /** The height a plate needs to brake to a safe landing from its speed now. */
    private static double need(FastFoodMatch.Plate plate) {
        double brake = FastFoodMatch.FOODS.get(plate.food()).brake() * (plate.big() ? FastFoodMatch.BIG_BRAKE : 1);

        return (plate.v() * plate.v() - FastFoodMatch.SAFE_SPEED * FastFoodMatch.SAFE_SPEED) / (2 * brake);
    }

    /** Lets the time go by, a step at a time, until the plate is at most this high. */
    private static long fallTo(FastFoodMatch match, int userId, double height, long now) {
        while (plate(match, userId).y() > height) {
            now += STEP;
            match.advance(now);
        }

        return now;
    }

    /** Lets the time go by until the plate is served or lost. */
    private static long settle(FastFoodMatch match, int userId, long now) {
        FastFoodMatch.Plate plate = plate(match, userId);

        for (int i = 0; i < 3000 && !plate.ended(); i++) {
            now += STEP;
            match.advance(now);
        }

        return now;
    }

    /** Drops a plate and opens its parachute this much above the height it needs. */
    private static long serve(FastFoodMatch match, int userId, double margin, long now) {
        match.drop(userId, now);

        FastFoodMatch.Plate plate = plate(match, userId);

        while (plate.y() > need(plate) + margin) {
            now += STEP;
            match.advance(now);
        }

        match.chute(userId, -1, now);
        return settle(match, userId, now);
    }

    @Test
    void aGameNeedsTwoPlayers() {
        FastFoodMatch match = new FastFoodMatch(1, FastFoodRules.standard(), new Random(1));

        match.join(1, "P1", "", "M", NOW);

        assertEquals(
                "not_enough_players",
                assertThrows(FastFoodException.class, () -> match.start(1, NOW)).code());

        match.addBot(1, NOW);
        match.start(1, NOW);

        assertEquals(FastFoodMatch.State.COUNTDOWN, match.state());
        assertEquals(1, match.hostId());
    }

    @Test
    void aPlateNeverBrakedIsLost() {
        FastFoodMatch match = game(6, false);
        long now = go();

        match.drop(1, now);
        now = settle(match, 1, now);

        FastFoodMatch.Plate plate = plate(match, 1);

        assertEquals(FastFoodMatch.Phase.CRASHED, plate.phase());
        assertEquals(0, match.player(1).stars());

        // A new plate comes after the plate timer.
        int lost = plate.id();

        match.advance(now + FastFoodMatch.PLATE_TIMER_MS + STEP);
        assertTrue(plate(match, 1).id() != lost);
        assertEquals(FastFoodMatch.Phase.HANGING, plate(match, 1).phase());
    }

    @Test
    void aParachuteOpenedInTimeServesAStar() {
        FastFoodMatch match = game(6, false);

        serve(match, 1, 0.05, go());

        assertEquals(FastFoodMatch.Phase.LANDED, plate(match, 1).phase());
        assertEquals(1, match.player(1).stars());
    }

    @Test
    void aParachuteOpenedTooLateLosesThePlate() {
        FastFoodMatch match = game(6, false);
        long now = go();

        match.drop(1, now);

        FastFoodMatch.Plate plate = plate(match, 1);

        while (plate.y() > need(plate) * 0.4) {
            now += STEP;
            match.advance(now);
        }

        match.chute(1, -1, now);
        settle(match, 1, now);

        assertEquals(FastFoodMatch.Phase.CRASHED, plate.phase());
        assertEquals(0, match.player(1).stars());
    }

    @Test
    void theParachuteOpensWhenThePlayerSawIt() {
        FastFoodMatch match = game(6, false);
        long now = go();

        match.drop(1, now);

        FastFoodMatch.Plate plate = plate(match, 1);

        while (plate.y() > need(plate) + 0.04) {
            now += STEP;
            match.advance(now);
        }

        long seen = now - go();

        // The click arrives 200 ms later, when opening now would be too late.
        now += 200;
        match.advance(now);
        match.chute(1, seen, now);
        settle(match, 1, now);

        assertEquals(FastFoodMatch.Phase.LANDED, plate.phase());
    }

    @Test
    void aClickTooOldCountsOnlyAsLateAsAllowed() {
        FastFoodMatch match = game(6, false);
        long now = go();

        match.drop(1, now);
        now = fallTo(match, 1, 0.5, now);

        long elapsed = now - go();

        match.chute(1, 0, now);

        FastFoodMatch.Plate plate = plate(match, 1);

        assertEquals(FastFoodMatch.Phase.CHUTE, plate.phase());
        assertEquals(go() + ((elapsed - FastFoodMatch.LAG_MS) / STEP) * STEP, plate.chuteAt);
    }

    @Test
    void droppingBeforeTheStartLosesThePlate() {
        FastFoodMatch match = new FastFoodMatch(1, new FastFoodRules(2, 6, true, false), new Random(5));

        match.join(1, "P1", "", "M", NOW);
        match.join(2, "P2", "", "F", NOW);
        match.start(1, NOW);
        match.drop(1, NOW + 1000);

        FastFoodMatch.Plate plate = plate(match, 1);

        assertTrue(plate.falseStart());
        assertThrows(FastFoodException.class, () -> match.chute(1, -1, NOW + 1200));

        long now = settle(match, 1, NOW + 1000);

        assertEquals(FastFoodMatch.Phase.CRASHED, plate.phase());
        assertTrue(match.log().stream().anyMatch(event -> event.type().equals("falsestart")));

        // The next plate waits the plate timer and the penalty.
        match.advance(now + FastFoodMatch.PLATE_TIMER_MS + STEP);
        assertEquals(plate.id(), plate(match, 1).id());
        match.advance(now + FastFoodMatch.PLATE_TIMER_MS + FastFoodMatch.FALSE_START_PENALTY_MS + STEP);
        assertTrue(plate(match, 1).id() != plate.id());
    }

    @Test
    void aMissileBurstsTheParachuteOfThePlayerInTheLead() {
        FastFoodMatch match = game(6, true);
        long now = go();

        match.player(1).held = FastFoodMatch.MISSILE;
        match.drop(2, now);
        now = fallTo(match, 2, 0.75, now);
        match.chute(2, -1, now);
        match.usePower(1, now);

        assertEquals(0, match.player(1).held());

        now += FastFoodMatch.MISSILE_MS + STEP;
        match.advance(now);

        FastFoodMatch.Plate plate = plate(match, 2);

        assertEquals(FastFoodMatch.Phase.BROKEN, plate.phase());
        settle(match, 2, now);
        assertEquals(FastFoodMatch.Phase.CRASHED, plate.phase());
    }

    @Test
    void aShieldStopsAMissile() {
        FastFoodMatch match = game(6, true);
        long now = go();

        match.player(1).held = FastFoodMatch.MISSILE;
        match.player(2).held = FastFoodMatch.SHIELD;
        match.usePower(2, now);
        match.drop(2, now);
        now = fallTo(match, 2, 0.8, now);
        match.chute(2, -1, now);
        match.usePower(1, now);
        now += FastFoodMatch.MISSILE_MS + STEP;
        match.advance(now);

        FastFoodMatch.Plate plate = plate(match, 2);

        assertEquals(FastFoodMatch.Phase.CHUTE, plate.phase());
        assertFalse(plate.shield());
        settle(match, 2, now);
        assertEquals(FastFoodMatch.Phase.LANDED, plate.phase());
    }

    @Test
    void noMissileWithoutAPlateInTheAir() {
        FastFoodMatch match = game(6, true);

        match.player(1).held = FastFoodMatch.MISSILE;

        assertEquals(
                "no_target",
                assertThrows(FastFoodException.class, () -> match.usePower(1, go()))
                        .code());
    }

    @Test
    void theBigParachuteOpensAtTheLastMoment() {
        FastFoodMatch match = game(6, true);
        long now = go();

        match.player(1).held = FastFoodMatch.BIG_PARACHUTE;
        match.usePower(1, now);
        match.drop(1, now);

        FastFoodMatch.Plate plate = plate(match, 1);

        assertTrue(plate.big());
        now = fallTo(match, 1, 0.06, now);
        match.chute(1, -1, now);
        settle(match, 1, now);

        assertEquals(FastFoodMatch.Phase.LANDED, plate.phase());
    }

    @Test
    void theFirstToServeAllPlatesWins() {
        FastFoodMatch match = game(3, false);
        long now = go();

        for (int served = 0; served < 3; served++) {
            while (plate(match, 1).phase() != FastFoodMatch.Phase.HANGING
                    || plate(match, 1).readyAt() > now) {
                now += STEP;
                match.advance(now);
            }

            now = serve(match, 1, 0.08, now);
        }

        assertEquals(FastFoodMatch.State.FINISHED, match.state());
        assertEquals(List.of(1), match.winners());
        assertTrue(match.takeRecord());
        assertFalse(match.takeRecord());
    }

    @Test
    void robotsPlayAWholeGame() {
        FastFoodMatch match = new FastFoodMatch(1, new FastFoodRules(3, 6, true, true), new Random(11));

        match.join(1, "P1", "", "M", NOW);
        match.addBot(1, NOW);
        match.addBot(1, NOW);
        match.start(1, NOW);

        long now = NOW;

        while (match.state() != FastFoodMatch.State.FINISHED && now < NOW + FastFoodMatch.GAME_MS + 10_000L) {
            now += 1000;
            match.advance(now);
        }

        assertEquals(FastFoodMatch.State.FINISHED, match.state());
        assertEquals(1, match.winners().size());

        FastFoodMatch.Player winner = match.player(match.winners().get(0));

        assertNotNull(winner);
        assertTrue(winner.bot());
        assertEquals(6, winner.stars());
        assertTrue(match.log().stream().anyMatch(event -> event.type().equals("land")));
    }

    @Test
    void rulesAndSeats() {
        FastFoodMatch match = new FastFoodMatch(1, FastFoodRules.standard(), new Random(1));

        match.join(1, "P1", "", "M", NOW);
        match.addBot(1, NOW);
        match.addBot(1, NOW);

        assertEquals(
                "table_full",
                assertThrows(FastFoodException.class, () -> match.addBot(1, NOW))
                        .code());

        match.setRules(1, new FastFoodRules(2, 4, true, true));

        assertEquals(2, match.players().size());
        assertTrue(match.players().stream().allMatch(player -> player.seat() < 2));
        assertThrows(FastFoodException.class, () -> match.setRules(2, FastFoodRules.standard()));
    }
}
