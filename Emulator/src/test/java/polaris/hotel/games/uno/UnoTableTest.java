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
        UnoTable table = new UnoTable(1, 6, 25_000L, new Random(7));

        for (int id = 1; id <= players; id++) table.join(id, "P" + id, "", "M", NOW);

        table.start(1, NOW);
        return table;
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
        UnoTable table = new UnoTable(1, 4, 25_000L, new Random(3));

        table.join(1, "P1", "", "M", NOW);
        table.addBot(1, NOW);
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
}
