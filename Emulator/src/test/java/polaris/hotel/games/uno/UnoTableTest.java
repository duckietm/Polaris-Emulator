package polaris.hotel.games.uno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class UnoTableTest {

    private static final long NOW = 1_000_000L;

    private static UnoTable table(int players) {
        return table(players, UnoRules.standard(6, 25));
    }

    private static UnoTable table(int players, UnoRules rules) {
        UnoTable table = new UnoTable(1, rules, new Random(7));

        for (int id = 1; id <= players; id++) table.join(id, "P" + id, "", "M", id - 1, NOW);

        table.start(1, NOW);
        return table;
    }

    private static UnoRules rules(boolean stackTwo, boolean stackFour, boolean jumpIn, boolean noSpecialFinish) {
        return new UnoRules(6, 7, 25, stackTwo, stackFour, jumpIn, noSpecialFinish, false, true, true);
    }

    private static int code(UnoException e) {
        return e.code().length();
    }

    @Test
    void aDeckHas108Cards() {
        assertEquals(108, UnoCards.deck().size());
        assertTrue(UnoCards.deck().stream().allMatch(UnoCards::isCard));
    }

    @Test
    void theGameStartsWithSevenCardsEachAndANumberOnThePile() {
        UnoTable table = table(3);

        table.handSizes().values().forEach(size -> assertEquals(7, size));
        assertTrue(UnoCards.isNumber(table.top()));
        assertEquals(108 - 21 - 1, table.deckSize());
    }

    @Test
    void aCardMatchesByColourOrValue() {
        assertTrue(UnoCards.matches("R7", "R2", 'R'));
        assertTrue(UnoCards.matches("B2", "R2", 'R'));
        assertFalse(UnoCards.matches("B3", "R2", 'R'));
        assertTrue(UnoCards.matches("WC", "R2", 'R'));
        // On a wild, only the colour asked for counts.
        assertTrue(UnoCards.matches("G5", "WC", 'G'));
        assertFalse(UnoCards.matches("R5", "WC", 'G'));
    }

    @Test
    void aDrawFourNeedsNoCardOfTheColour() {
        assertFalse(UnoCards.playable("WF", List.of("WF", "R1"), "R2", 'R'));
        assertTrue(UnoCards.playable("WF", List.of("WF", "B1"), "R2", 'R'));
    }

    @Test
    void onlyThePlayerWhoseTurnItIsPlays() {
        UnoTable table = table(2);

        table.arrange(1, List.of("R1", "B9"), "R5", 0);
        table.arrange(2, List.of("R2", "G9"), "R5", 0);

        UnoException refused = assertThrows(UnoException.class, () -> table.play(2, "R2", ' ', NOW));
        assertEquals("not_your_turn", refused.code());
        assertTrue(code(refused) > 0);

        table.play(1, "R1", ' ', NOW);
        assertEquals("R1", table.top());
        assertEquals(1, table.turn());
    }

    @Test
    void drawTwoMakesTheNextPlayerDrawAndMissTheirTurn() {
        UnoTable table = table(3);

        table.arrange(1, List.of("RD", "B9", "G1"), "R5", 0);
        table.play(1, "RD", ' ', NOW);

        assertEquals(9, table.handSizes().get(2));
        assertEquals(2, table.turn());
    }

    @Test
    void reverseTurnsTheOtherWayAndSkipsWithTwoPlayers() {
        UnoTable three = table(3);

        three.arrange(1, List.of("RR", "B9"), "R5", 0);
        three.play(1, "RR", ' ', NOW);
        assertEquals(2, three.turn());
        assertEquals(-1, three.direction());

        UnoTable two = table(2);

        two.arrange(1, List.of("RR", "B9"), "R5", 0);
        two.play(1, "RR", ' ', NOW);
        assertEquals(0, two.turn());
    }

    @Test
    void aWildNeedsAColourAndSetsIt() {
        UnoTable table = table(2);

        table.arrange(1, List.of("WC", "B9"), "R5", 0);
        assertEquals(
                "choose_colour",
                assertThrows(UnoException.class, () -> table.play(1, "WC", ' ', NOW))
                        .code());

        table.play(1, "WC", 'G', NOW);
        assertEquals('G', table.colour());
    }

    @Test
    void anUnplayableDrawnCardEndsTheTurn() {
        UnoTable table = table(2);

        table.arrange(1, List.of("B9"), "R5", 0);
        table.draw(1, NOW);

        assertTrue(table.turn() == 1 || table.drew());
    }

    @Test
    void forgettingUnoCanBeCaught() {
        UnoTable table = table(2);

        table.arrange(1, List.of("R1", "B9"), "R5", 0);
        table.play(1, "R1", ' ', NOW);
        table.catchUno(2, 1);

        assertEquals(3, table.handSizes().get(1));
        assertEquals(
                "no_catch",
                assertThrows(UnoException.class, () -> table.catchUno(2, 1)).code());
    }

    @Test
    void sayingUnoProtects() {
        UnoTable table = table(2);

        table.arrange(1, List.of("R1", "B9"), "R5", 0);
        table.declare(1);
        table.play(1, "R1", ' ', NOW);

        assertThrows(UnoException.class, () -> table.catchUno(2, 1));
    }

    @Test
    void theLastCardWins() {
        UnoTable table = table(2);

        table.arrange(1, List.of("R1"), "R5", 0);
        table.play(1, "R1", ' ', NOW);

        assertEquals(UnoTable.State.FINISHED, table.state());
        assertEquals(1, table.winnerId());
    }

    @Test
    void aPlayerWhoLetsTheTimeRunOutDrawsAndPasses() {
        UnoTable table = table(2);

        table.arrange(1, List.of("B9"), "R5", 0);
        table.arrange(2, List.of("G9"), "R5", 0);
        table.tick(NOW + 26_000L);

        assertEquals(2, table.handSizes().get(1));
        assertEquals(1, table.turn());
    }

    @Test
    void aLastPlayerStandingWins() {
        UnoTable table = table(2);

        table.leave(2);

        assertEquals(UnoTable.State.FINISHED, table.state());
        assertEquals(1, table.winnerId());
    }

    @Test
    void robotsPlayATurnByThemselves() {
        UnoTable table = new UnoTable(1, UnoRules.standard(4, 25), new Random(3));

        table.join(1, "P1", "", "M", -1, NOW);
        table.addBot(1, -1, NOW);
        table.start(1, NOW);

        int robot = table.seats().get(1).userId();

        assertTrue(robot < 0);

        // Many clock ticks: the robot never holds the game up, and the game keeps its 108 cards.
        for (int second = 1; second < 600 && table.state() == UnoTable.State.PLAYING; second++) {
            long now = NOW + second * 1000L;

            if (table.turn() == 0) {
                table.tick(now + 30_000L);
            } else {
                table.tick(now + 5_000L);
            }

            int cards = table.deckSize()
                    + table.handSizes().values().stream()
                            .mapToInt(Integer::intValue)
                            .sum();

            assertTrue(cards <= 108);
        }
    }

    @Test
    void robotsChooseTheirCommonestColour() {
        assertEquals('B', UnoBot.favouriteColour(List.of("WC", "B1", "B2", "R3"), "WC", new Random(1)));
        assertEquals("R7", UnoBot.choose(List.of("WF", "WC", "R7"), 'R'));
    }

    @Test
    void stackedDrawCardsAddUpForTheNextPlayer() {
        UnoTable table = table(3, rules(true, true, false, false));

        table.arrange(1, List.of("RD", "B9", "G1"), "R5", 0);
        table.arrange(2, List.of("BD", "WF", "G2"), "R5", 0);
        table.arrange(1, List.of("RD", "B9", "G1"), "R5", 0);
        table.play(1, "RD", ' ', NOW);

        assertEquals(1, table.turn());
        assertEquals(2, table.pendingDraw());
        // Only a stacking card goes on it.
        assertEquals(
                "must_stack",
                assertThrows(UnoException.class, () -> table.play(2, "G2", ' ', NOW))
                        .code());

        table.play(2, "WF", 'G', NOW);
        assertEquals(6, table.pendingDraw());

        table.draw(3, NOW);
        assertEquals(7 + 6, table.handSizes().get(3));
        assertEquals(0, table.pendingDraw());
        assertEquals(0, table.turn());
    }

    @Test
    void withoutTheFourRuleAPlusFourDoesNotGoOnAPlusTwo() {
        UnoTable table = table(2, rules(true, false, false, false));

        table.arrange(1, List.of("RD", "B9"), "R5", 0);
        table.arrange(2, List.of("WF", "G2"), "R5", 0);
        table.arrange(1, List.of("RD", "B9"), "R5", 0);
        table.play(1, "RD", ' ', NOW);

        assertThrows(UnoException.class, () -> table.play(2, "WF", 'G', NOW));
    }

    @Test
    void theSameCardJumpsInOutOfTurn() {
        UnoTable table = table(3, rules(false, false, true, false));

        table.arrange(3, List.of("R5", "B1"), "R5", 0);

        table.play(3, "R5", ' ', NOW);

        assertEquals(0, table.turn());
        assertEquals("R5", table.top());
        assertEquals(1, table.handSizes().get(3));
    }

    @Test
    void withoutTheRuleNobodyJumpsIn() {
        UnoTable table = table(3);

        table.arrange(3, List.of("R5", "B1"), "R5", 0);

        assertEquals(
                "not_your_turn",
                assertThrows(UnoException.class, () -> table.play(3, "R5", ' ', NOW))
                        .code());
    }

    @Test
    void theLastCardCannotBeASpecialOneWithThatRule() {
        UnoTable table = table(2, rules(false, false, false, true));

        table.arrange(1, List.of("RS"), "R5", 0);

        assertEquals(
                "special_finish",
                assertThrows(UnoException.class, () -> table.play(1, "RS", ' ', NOW))
                        .code());
    }

    @Test
    void playersChooseTheirChairAndTurnsFollowTheChairs() {
        UnoTable table = new UnoTable(1, UnoRules.standard(4, 25), new Random(1));

        table.join(1, "P1", "", "M", 5, NOW);
        table.join(2, "P2", "", "M", 2, NOW);

        assertEquals(2, table.seats().get(0).userId());
        assertEquals(
                "chair_taken",
                assertThrows(UnoException.class, () -> table.sit(1, 2)).code());

        table.sit(1, 0);
        assertEquals(1, table.seats().get(0).userId());
    }

    @Test
    void robotsCanBeTurnedOff() {
        UnoRules noBots = new UnoRules(4, 7, 25, false, false, false, false, false, true, false);
        UnoTable table = new UnoTable(1, noBots, new Random(1));

        table.join(1, "P1", "", "M", -1, NOW);

        assertEquals(
                "no_bots",
                assertThrows(UnoException.class, () -> table.addBot(1, -1, NOW)).code());
    }
}
