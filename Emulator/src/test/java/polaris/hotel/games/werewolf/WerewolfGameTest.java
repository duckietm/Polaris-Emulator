package polaris.hotel.games.werewolf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WerewolfGameTest {

    private static final long NOW = 1_000_000L;

    /** Six humans (ids 1-6), roles dealt in seat order. */
    private static WerewolfGame village(WerewolfRules rules, WerewolfRole... roles) {
        WerewolfGame game = new WerewolfGame(1, rules, new Random(5));

        for (int id = 1; id <= roles.length; id++) game.join(id, "P" + id, "", "M", id - 1, NOW);

        game.start(1, NOW);
        game.deal(List.of(roles));
        return game;
    }

    private static WerewolfRules plain() {
        return new WerewolfRules(12, false, false, false, false, false, true, true, 2);
    }

    /** Lets the clock run until the step is the one wanted (or a long time passed). */
    private static long runTo(WerewolfGame game, WerewolfGame.Step step, long now) {
        for (int second = 0;
                second < 600 && game.step() != step && game.state() == WerewolfGame.State.PLAYING;
                second++) {
            now += 1000L;
            game.tick(now);
        }

        return now;
    }

    @Test
    void theCompositionFitsThePlayers() {
        WerewolfRules rules = WerewolfRules.standard();

        assertEquals(1, rules.composition(5).stream().filter(WerewolfRole::wolf).count());
        assertEquals(2, rules.composition(8).stream().filter(WerewolfRole::wolf).count());
        assertEquals(
                3, rules.composition(12).stream().filter(WerewolfRole::wolf).count());
        assertEquals(8, rules.composition(8).size());
        assertTrue(rules.composition(5).contains(WerewolfRole.VILLAGER));
    }

    @Test
    void aGameNeedsFivePlayers() {
        WerewolfGame game = new WerewolfGame(1, plain(), new Random(1));

        game.join(1, "P1", "", "M", -1, NOW);

        assertEquals(
                "not_enough_players",
                assertThrows(WerewolfException.class, () -> game.start(1, NOW)).code());
    }

    @Test
    void thereAreAsManyStumpsAsPlayers() {
        WerewolfGame game = new WerewolfGame(1, plain(), new Random(1));

        game.join(1, "P1", "", "M", 11, NOW);
        game.join(2, "P2", "", "M", 3, NOW);
        game.setRules(1, new WerewolfRules(6, false, false, false, false, false, true, true, 2));

        assertTrue(game.seat(1).chair < 6);
        assertEquals(3, game.seat(2).chair);
        // The first player stays the host, whatever the stumps.
        assertEquals(1, game.hostId());
        assertEquals(
                "chair_taken",
                assertThrows(WerewolfException.class, () -> game.sit(1, 7)).code());
    }

    @Test
    void theWerewolvesEatAndTheVillageVotes() {
        WerewolfGame game = village(
                plain(),
                WerewolfRole.WEREWOLF,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER);

        assertEquals(WerewolfGame.Step.WOLVES, game.step());
        assertTrue(game.isNight());
        assertEquals(
                "bad_target",
                assertThrows(WerewolfException.class, () -> game.act(1, 1, 0, NOW))
                        .code());
        assertEquals(
                "not_your_turn",
                assertThrows(WerewolfException.class, () -> game.act(2, 3, 0, NOW))
                        .code());

        game.act(1, 2, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.DEBATE, NOW);

        assertFalse(game.seat(2).alive());
        assertFalse(game.isNight());

        now = runTo(game, WerewolfGame.Step.VOTE, now);

        for (int voter = 3; voter <= 6; voter++) game.act(voter, 1, 0, now);
        runTo(game, WerewolfGame.Step.NONE, now);

        assertEquals(WerewolfGame.State.FINISHED, game.state());
        assertEquals("village", game.winner());
    }

    @Test
    void theVoteEndsOnceEveryoneVoted() {
        WerewolfGame game = village(
                plain(),
                WerewolfRole.WEREWOLF,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER);

        game.act(1, 2, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.VOTE, NOW);

        game.act(1, 3, 0, now);
        game.act(3, 4, 0, now);
        game.act(4, 3, 0, now);
        game.act(5, 3, 0, now);
        game.act(6, 3, 0, now);
        game.tick(now + 2000L);

        assertFalse(game.seat(3).alive());
        assertTrue(game.isNight());
    }

    @Test
    void aTiedVoteKillsNobody() {
        WerewolfGame game = village(
                plain(),
                WerewolfRole.WEREWOLF,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER);

        game.act(1, 2, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.VOTE, NOW);

        game.act(3, 4, 0, now);
        game.act(4, 3, 0, now);
        now = runTo(game, WerewolfGame.Step.WOLVES, now);

        assertTrue(game.seat(3).alive());
        assertTrue(game.seat(4).alive());
        assertEquals(2, game.night());
    }

    @Test
    void theGuardSavesAndCannotGuardTheSamePlayerTwice() {
        WerewolfRules rules = new WerewolfRules(12, false, false, false, false, true, true, true, 2);
        WerewolfGame game = new WerewolfGame(1, rules, new Random(5));

        for (int id = 1; id <= 6; id++) game.join(id, "P" + id, "", "M", id - 1, NOW);

        // The guard's step comes before the wolves': deal before the night starts.
        game.start(1, NOW);
        game.deal(List.of(
                WerewolfRole.WEREWOLF,
                WerewolfRole.GUARD,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER));

        assertEquals(WerewolfGame.Step.GUARD, game.step());

        game.act(2, 3, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.WOLVES, NOW);

        game.act(1, 3, 0, now);
        now = runTo(game, WerewolfGame.Step.DEBATE, now);

        assertTrue(game.seat(3).alive());

        now = runTo(game, WerewolfGame.Step.GUARD, now);
        assertEquals(
                "same_protection",
                assertThrows(WerewolfException.class, () -> game.act(2, 3, 0, NOW))
                        .code());
    }

    @Test
    void theSeerLearnsARole() {
        WerewolfRules rules = new WerewolfRules(12, true, false, false, false, false, true, true, 2);
        WerewolfGame game = village(
                rules,
                WerewolfRole.WEREWOLF,
                WerewolfRole.SEER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER);

        assertEquals(WerewolfGame.Step.SEER, game.step());

        game.act(2, 1, 0, NOW);

        WerewolfGame.Event seen = game.log().get(game.log().size() - 1);

        assertEquals("seer_sees", seen.type());
        assertEquals("werewolf", seen.b());
        assertTrue(game.visible(seen, game.seat(2)));
        assertFalse(game.visible(seen, game.seat(3)));
    }

    @Test
    void theWitchSavesThePrey() {
        WerewolfRules rules = new WerewolfRules(12, false, true, false, false, false, true, true, 2);
        WerewolfGame game = village(
                rules,
                WerewolfRole.WEREWOLF,
                WerewolfRole.WITCH,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER);

        game.act(1, 3, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.WITCH, NOW);

        game.witch(2, true, 0, now);
        runTo(game, WerewolfGame.Step.DEBATE, now);

        assertTrue(game.seat(3).alive());
        assertFalse(game.lifePotion());
    }

    @Test
    void loversDieTogether() {
        WerewolfRules rules = new WerewolfRules(12, false, false, false, true, false, true, true, 2);
        WerewolfGame game = village(
                rules,
                WerewolfRole.WEREWOLF,
                WerewolfRole.CUPID,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER);

        assertEquals(WerewolfGame.Step.CUPID, game.step());

        game.act(2, 3, 4, NOW);
        long now = runTo(game, WerewolfGame.Step.WOLVES, NOW);

        game.act(1, 3, 0, now);
        runTo(game, WerewolfGame.Step.DEBATE, now);

        assertFalse(game.seat(3).alive());
        assertFalse(game.seat(4).alive());
    }

    @Test
    void theHunterShootsWhenHeDies() {
        WerewolfRules rules = new WerewolfRules(12, false, false, true, false, false, true, true, 2);
        WerewolfGame game = village(
                rules,
                WerewolfRole.WEREWOLF,
                WerewolfRole.HUNTER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER);

        game.act(1, 2, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.HUNTER, NOW);

        assertEquals(WerewolfGame.Step.HUNTER, game.step());

        game.act(2, 1, 0, now);
        runTo(game, WerewolfGame.Step.NONE, now);

        assertFalse(game.seat(1).alive());
        assertEquals("village", game.winner());
    }

    @Test
    void theHunterShootsBeforeTheWerewolvesWin() {
        WerewolfRules rules = new WerewolfRules(12, false, false, true, true, false, true, true, 2);
        WerewolfGame game = village(
                rules,
                WerewolfRole.WEREWOLF,
                WerewolfRole.CUPID,
                WerewolfRole.HUNTER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER);

        game.act(2, 3, 4, NOW);
        long now = runTo(game, WerewolfGame.Step.WOLVES, NOW);

        game.act(1, 5, 0, now);
        now = runTo(game, WerewolfGame.Step.VOTE, now);

        for (int voter = 1; voter <= 4; voter++) game.act(voter, voter == 2 ? 1 : 2, 0, now);
        now = runTo(game, WerewolfGame.Step.WOLVES, now);

        // The hunter dies, his lover follows: only the werewolf is left, but the hunter shoots first.
        game.act(1, 3, 0, now);
        now = runTo(game, WerewolfGame.Step.HUNTER, now);

        assertEquals(WerewolfGame.Step.HUNTER, game.step());
        assertEquals(WerewolfGame.State.PLAYING, game.state());

        game.act(3, 1, 0, now);
        runTo(game, WerewolfGame.Step.NONE, now);

        assertFalse(game.seat(1).alive());
        assertEquals("nobody", game.winner());
    }

    @Test
    void theWerewolvesWinWhenOnlyTheyRemain() {
        WerewolfGame game = new WerewolfGame(1, plain(), new Random(5));

        game.join(1, "P1", "", "M", -1, NOW);

        for (int bot = 0; bot < 4; bot++) game.addBot(1, NOW);

        game.start(1, NOW);

        // Robots play every role: the game always ends.
        runTo(game, WerewolfGame.Step.NONE, NOW);
        runTo(game, WerewolfGame.Step.NONE, NOW + 600_000L);

        assertTrue(game.state() == WerewolfGame.State.FINISHED || game.night() > 1);
    }

    @Test
    void theNightIsSilentExceptForThePack() {
        WerewolfGame game = village(
                plain(),
                WerewolfRole.WEREWOLF,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER,
                WerewolfRole.VILLAGER);

        assertEquals(
                "village_sleeps",
                assertThrows(WerewolfException.class, () -> game.say(2, "bonjour"))
                        .code());

        game.say(1, "on mange qui ?");

        WerewolfGame.Event said = game.log().get(game.log().size() - 1);

        assertEquals(WerewolfGame.Channel.WOLVES, said.channel());
        assertFalse(game.visible(said, game.seat(2)));
    }
}
