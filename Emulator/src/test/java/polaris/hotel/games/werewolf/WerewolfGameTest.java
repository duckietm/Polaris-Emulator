package polaris.hotel.games.werewolf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WerewolfGameTest {

    private static final long NOW = 1_000_000L;

    private static WerewolfRules plain() {
        return new WerewolfRules(12, 0, Set.of(), true, true, 2);
    }

    /** Humans with ids 1 to n, sitting in that order round the fire, dealt these roles in that order. */
    private static WerewolfGame village(WerewolfRole... roles) {
        WerewolfGame game = new WerewolfGame(1, plain(), new Random(5));

        for (int id = 1; id <= roles.length; id++) game.join(id, "P" + id, "", "M", id - 1, NOW);

        game.startWith(1, NOW, List.of(roles));
        return game;
    }

    private static WerewolfRole[] withVillagers(int players, WerewolfRole... first) {
        WerewolfRole[] roles = new WerewolfRole[players];

        for (int index = 0; index < players; index++)
            roles[index] = index < first.length ? first[index] : WerewolfRole.VILLAGER;

        return roles;
    }

    /** Lets the clock run until the step is the one wanted (or a long time passed). */
    private static long runTo(WerewolfGame game, WerewolfGame.Step step, long now) {
        for (int second = 0;
                second < 900 && game.step() != step && game.state() == WerewolfGame.State.PLAYING;
                second++) {
            now += 1000L;
            game.tick(now);
        }

        return now;
    }

    private static boolean logged(WerewolfGame game, String type, String a) {
        for (WerewolfGame.Event event : game.log()) {
            if (event.type().equals(type) && (a == null || event.a().equals(a))) return true;
        }

        return false;
    }

    // ---- The village

    @Test
    void theCompositionFitsThePlayers() {
        WerewolfRules rules = WerewolfRules.standard();
        Random random = new Random(3);

        assertEquals(
                1,
                rules.composition(5, random).stream().filter(WerewolfRole::wolf).count());
        assertEquals(
                2,
                rules.composition(8, random).stream().filter(WerewolfRole::wolf).count());
        assertEquals(
                3,
                rules.composition(12, random).stream()
                        .filter(WerewolfRole::wolf)
                        .count());
        assertEquals(8, rules.composition(8, random).size());
    }

    @Test
    void theSpecialWolvesAreAmongTheWolvesAndTheSistersComeInPairs() {
        WerewolfRules rules =
                new WerewolfRules(8, 2, EnumSet.of(WerewolfRole.BIG_BAD_WOLF, WerewolfRole.SISTER), true, true, 2);
        List<WerewolfRole> roles = rules.composition(8, new Random(1));

        assertEquals(2, roles.stream().filter(WerewolfRole::wolf).count());
        assertTrue(roles.contains(WerewolfRole.BIG_BAD_WOLF));
        assertEquals(
                2, roles.stream().filter(role -> role == WerewolfRole.SISTER).count());
    }

    @Test
    void moreRolesThanPlacesAreDrawn() {
        WerewolfRules rules = new WerewolfRules(5, 0, EnumSet.copyOf(WerewolfRole.specials()), true, true, 2);
        List<WerewolfRole> roles = rules.composition(5, new Random(9));

        assertEquals(5, roles.size());
        assertEquals(1, roles.stream().filter(WerewolfRole::wolf).count());
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
        game.setRules(1, new WerewolfRules(6, 0, Set.of(), true, true, 2));

        assertTrue(game.seat(1).chair < 6);
        assertEquals(3, game.seat(2).chair);
        // The first player stays the host, whatever the stumps.
        assertEquals(1, game.hostId());
        assertEquals(
                "chair_taken",
                assertThrows(WerewolfException.class, () -> game.sit(1, 7)).code());
    }

    // ---- Night and day

    @Test
    void theWerewolvesEatAndTheVillageVotes() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF));

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
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF));

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
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF));

        game.act(1, 2, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.VOTE, NOW);

        game.act(3, 4, 0, now);
        game.act(4, 3, 0, now);
        runTo(game, WerewolfGame.Step.WOLVES, now);

        assertTrue(game.seat(3).alive());
        assertTrue(game.seat(4).alive());
        assertEquals(2, game.night());
    }

    @Test
    void theNightIsSilentExceptForThePack() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF));

        assertEquals(
                "village_sleeps",
                assertThrows(WerewolfException.class, () -> game.say(2, "bonjour"))
                        .code());

        game.say(1, "on mange qui ?");

        WerewolfGame.Event said = game.log().get(game.log().size() - 1);

        assertEquals(WerewolfGame.Channel.WOLVES, said.channel());
        assertFalse(game.visible(said, game.seat(2)));
    }

    // ---- The classic roles

    @Test
    void theGuardSavesAndCannotGuardTheSamePlayerTwice() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.GUARD));

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
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.SEER));

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
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.WITCH));

        game.act(1, 3, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.WITCH, NOW);

        game.witch(2, true, 0, now);
        runTo(game, WerewolfGame.Step.DEBATE, now);

        assertTrue(game.seat(3).alive());
        assertFalse(game.lifePotion());
    }

    @Test
    void loversDieTogether() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.CUPID));

        assertEquals(WerewolfGame.Step.CUPID, game.step());

        game.act(2, 3, 4, NOW);
        long now = runTo(game, WerewolfGame.Step.WOLVES, NOW);

        game.act(1, 3, 0, now);
        runTo(game, WerewolfGame.Step.DEBATE, now);

        assertFalse(game.seat(3).alive());
        assertFalse(game.seat(4).alive());
    }

    @Test
    void theHunterPlayedByAPlayerShootsWhenHeDies() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.HUNTER));

        game.act(1, 2, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.HUNTER, NOW);

        assertEquals(WerewolfGame.Step.HUNTER, game.step());
        assertFalse(game.seat(2).alive());

        // Dead, he is asked for his shot, and only he is.
        JsonObject you = WerewolfViews.game(game, 2, now).getAsJsonObject("you");

        assertEquals("hunter", you.get("action").getAsString());
        assertEquals(
                "",
                WerewolfViews.game(game, 3, now)
                        .getAsJsonObject("you")
                        .get("action")
                        .getAsString());

        game.act(2, 1, 0, now);
        runTo(game, WerewolfGame.Step.NONE, now);

        assertFalse(game.seat(1).alive());
        assertTrue(logged(game, "hunter_shoots", "P2"));
        assertEquals("village", game.winner());
    }

    @Test
    void theHunterShootsBeforeTheWerewolvesWin() {
        WerewolfGame game = village(
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

        assertEquals(WerewolfGame.State.PLAYING, game.state());

        game.act(3, 1, 0, now);
        runTo(game, WerewolfGame.Step.NONE, now);

        assertFalse(game.seat(1).alive());
        assertEquals("nobody", game.winner());
    }

    // ---- The roles of the village

    @Test
    void theElderSurvivesOneAttackAndTheVillageThatKillsHimLosesItsPowers() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.ELDER, WerewolfRole.SEER));

        long now = runTo(game, WerewolfGame.Step.WOLVES, NOW);

        game.act(1, 2, 0, now);
        now = runTo(game, WerewolfGame.Step.VOTE, now);

        assertTrue(game.seat(2).alive());

        for (int voter : new int[] {1, 3, 4, 5, 6}) game.act(voter, 2, 0, now);
        now = runTo(game, WerewolfGame.Step.SEER, now);

        assertFalse(game.seat(2).alive());
        assertTrue(game.powersLost());
        assertTrue(logged(game, "elder_curse", "P2"));
        assertEquals(
                "not_your_turn",
                assertThrows(WerewolfException.class, () -> game.act(3, 4, 0, NOW))
                        .code());
    }

    @Test
    void theVillageIdiotIsSparedByTheVoteButVotesNoMore() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.IDIOT));

        game.act(1, 3, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.VOTE, NOW);

        for (int voter : new int[] {1, 4, 5, 6}) game.act(voter, 2, 0, now);
        now = runTo(game, WerewolfGame.Step.WOLVES, now);

        assertTrue(game.seat(2).alive());
        assertTrue(game.seat(2).revealed());
        assertTrue(logged(game, "idiot_spared", "P2"));

        game.act(1, 4, 0, now);
        now = runTo(game, WerewolfGame.Step.VOTE, now);

        assertEquals(
                "not_your_turn",
                assertThrows(WerewolfException.class, () -> game.act(2, 1, 0, NOW))
                        .code());
    }

    @Test
    void theScapegoatDiesOnATie() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.SCAPEGOAT));

        game.act(1, 3, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.VOTE, NOW);

        game.act(4, 5, 0, now);
        game.act(5, 4, 0, now);
        runTo(game, WerewolfGame.Step.WOLVES, now);

        assertFalse(game.seat(2).alive());
        assertTrue(game.seat(4).alive());
        assertTrue(game.seat(5).alive());
    }

    @Test
    void theRavenAddsTwoVotes() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.RAVEN));

        assertEquals(WerewolfGame.Step.RAVEN, game.step());

        game.act(2, 5, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.WOLVES, NOW);

        game.act(1, 3, 0, now);
        now = runTo(game, WerewolfGame.Step.VOTE, now);

        assertEquals(2, game.votes().get(5));

        game.act(4, 6, 0, now);
        runTo(game, WerewolfGame.Step.WOLVES, now);

        assertFalse(game.seat(5).alive());
        assertTrue(game.seat(6).alive());
    }

    @Test
    void theFoxSmellsAWerewolfOrLosesHisNose() {
        WerewolfGame found = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.FOX));

        found.act(2, 6, 0, NOW);
        assertTrue(logged(found, "fox_yes", "P6"));
        assertTrue(found.foxActive());

        WerewolfGame missed = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.FOX));

        missed.act(2, 4, 0, NOW);
        assertTrue(logged(missed, "fox_no", "P4"));
        assertFalse(missed.foxActive());
    }

    @Test
    void theBearGrowlsNextToAWerewolf() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.BEAR_TAMER, WerewolfRole.WEREWOLF));

        game.act(2, 4, 0, NOW);
        runTo(game, WerewolfGame.Step.DEBATE, NOW);

        assertTrue(logged(game, "bear_growls", "P1"));
    }

    @Test
    void theKnightsRustySwordKillsTheNextWerewolf() {
        WerewolfGame game =
                village(withVillagers(7, WerewolfRole.KNIGHT, WerewolfRole.WEREWOLF, WerewolfRole.WEREWOLF));

        game.act(2, 1, 0, NOW);
        game.act(3, 1, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.DEBATE, NOW);

        now = runTo(game, WerewolfGame.Step.WOLVES, now);

        assertFalse(game.seat(1).alive());

        game.act(2, 4, 0, now);
        game.act(3, 4, 0, now);
        runTo(game, WerewolfGame.Step.DEBATE, now);

        assertFalse(game.seat(2).alive());
        assertTrue(game.seat(3).alive());

        boolean rust = false;

        for (WerewolfGame.Event event : game.log()) {
            if (event.type().equals("death") && event.a().equals("P2"))
                rust = event.c().equals("rust");
        }

        assertTrue(rust);
    }

    @Test
    void theLittleGirlPeeksAtThePackWithoutTheNames() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.LITTLE_GIRL));

        game.act(1, 3, 0, NOW);

        WerewolfGame.Event choice = game.log().get(game.log().size() - 1);

        assertEquals("wolf_choice", choice.type());
        assertTrue(game.visible(choice, game.seat(2)));
        assertFalse(game.visible(choice, game.seat(4)));

        for (JsonElement element : WerewolfViews.game(game, 2, NOW).getAsJsonArray("log")) {
            JsonObject entry = element.getAsJsonObject();

            if (entry.get("type").getAsString().equals("wolf_choice")) {
                assertEquals("", entry.get("a").getAsString());
                assertEquals("P3", entry.get("b").getAsString());
            }
        }
    }

    @Test
    void theWildChildBecomesAWerewolfWhenItsModelDies() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.WILD_CHILD));

        assertEquals(WerewolfGame.Step.WILD_CHILD, game.step());

        game.act(2, 3, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.WOLVES, NOW);

        game.act(1, 3, 0, now);
        runTo(game, WerewolfGame.Step.DEBATE, now);

        assertFalse(game.seat(3).alive());
        assertTrue(game.seat(2).pack());
        assertTrue(logged(game, "wild_child_turns", "P3"));
    }

    @Test
    void theWolfDogChoosesThePack() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.WOLF_DOG));

        assertEquals(WerewolfGame.Step.WOLF_DOG, game.step());

        game.choose(2, true, NOW);
        runTo(game, WerewolfGame.Step.WOLVES, NOW);

        assertTrue(game.seat(2).pack());
        assertTrue(game.isActor(game.seat(2)));
    }

    @Test
    void theSistersKnowEachOther() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.SISTER, WerewolfRole.SISTER));

        boolean told = false;

        for (WerewolfGame.Event event : game.log()) {
            if (event.type().equals("sister") && event.to() == 2)
                told = event.a().equals("P3");
        }

        assertTrue(told);
    }

    // ---- The special werewolves

    @Test
    void theAccursedFatherInfectsThePrey() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.ACCURSED_FATHER));

        game.act(1, 3, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.ACCURSED, NOW);

        game.choose(1, true, now);
        runTo(game, WerewolfGame.Step.DEBATE, now);

        assertTrue(game.seat(3).alive());
        assertTrue(game.seat(3).pack());
    }

    @Test
    void theBigBadWolfEatsASecondPrey() {
        WerewolfGame game = village(withVillagers(7, WerewolfRole.WEREWOLF, WerewolfRole.BIG_BAD_WOLF));

        game.act(1, 3, 0, NOW);
        game.act(2, 3, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.BIG_BAD_WOLF, NOW);

        game.act(2, 4, 0, now);
        runTo(game, WerewolfGame.Step.DEBATE, now);

        assertFalse(game.seat(3).alive());
        assertFalse(game.seat(4).alive());
    }

    @Test
    void theWhiteWolfEatsAWerewolfEverySecondNight() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WHITE_WOLF, WerewolfRole.WEREWOLF));

        game.act(1, 3, 0, NOW);
        game.act(2, 3, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.DEBATE, NOW);

        now = runTo(game, WerewolfGame.Step.WOLVES, now);

        assertEquals(2, game.night());

        game.act(1, 4, 0, now);
        game.act(2, 4, 0, now);
        now = runTo(game, WerewolfGame.Step.WHITE_WOLF, now);

        game.act(1, 2, 0, now);
        runTo(game, WerewolfGame.Step.DEBATE, now);

        assertFalse(game.seat(2).alive());
        assertTrue(game.seat(1).alive());
    }

    // ---- Alone

    @Test
    void thePiperWinsWhenEverybodyElseIsCharmed() {
        WerewolfGame game = village(withVillagers(5, WerewolfRole.WEREWOLF, WerewolfRole.PIPER));

        game.act(1, 3, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.PIPER, NOW);

        game.act(2, 4, 5, now);
        now = runTo(game, WerewolfGame.Step.VOTE, now);

        assertTrue(logged(game, "charmed", null));

        for (int voter : new int[] {2, 4, 5}) game.act(voter, 1, 0, now);
        runTo(game, WerewolfGame.Step.NONE, now);

        assertEquals("piper", game.winner());
        assertTrue(game.won(game.seat(2)));
        assertFalse(game.won(game.seat(4)));
    }

    @Test
    void theAngelWinsWhenEliminatedTheFirstDay() {
        WerewolfGame game = village(withVillagers(6, WerewolfRole.WEREWOLF, WerewolfRole.ANGEL));

        game.act(1, 3, 0, NOW);
        long now = runTo(game, WerewolfGame.Step.VOTE, NOW);

        for (int voter : new int[] {1, 4, 5, 6}) game.act(voter, 2, 0, now);
        runTo(game, WerewolfGame.Step.NONE, now);

        assertEquals("angel", game.winner());
        assertTrue(game.won(game.seat(2)));
    }

    // ---- Robots

    @Test
    void robotsPlayEveryRoleToTheEnd() {
        WerewolfRules rules = new WerewolfRules(12, 0, EnumSet.copyOf(WerewolfRole.specials()), true, true, 1);

        for (int seed = 1; seed <= 25; seed++) {
            WerewolfGame game = new WerewolfGame(1, rules, new Random(seed));

            game.join(1, "P1", "", "M", -1, NOW);
            game.fillWithBots(1, NOW);
            game.start(1, NOW);

            long now = NOW;

            for (int second = 0; second < 40_000 && game.state() == WerewolfGame.State.PLAYING; second++) {
                now += 1000L;
                game.tick(now);
            }

            assertEquals(WerewolfGame.State.FINISHED, game.state(), "seed " + seed);
            assertFalse(game.winner().isEmpty());
        }
    }
}
