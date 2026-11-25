package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredReferencePairingTest {
    private static List<Long> draw(WiredReferencePairing pairing, int count) {
        List<Long> drawn = new ArrayList<>();
        for (int i = 0; i < count; i++) drawn.add(pairing.next());
        return drawn;
    }

    @Test
    void asManyHoldersAsDestinationsGiveEveryDestinationADifferentHolder() {
        for (int seed = 0; seed < 50; seed++) {
            List<Long> drawn = draw(new WiredReferencePairing(List.of(1L, 2L, 3L, 4L), new Random(seed)), 3);
            assertEquals(3, new HashSet<>(drawn).size(), "three destinations, three different holders");
        }
    }

    @Test
    void fewerHoldersThanDestinationsShareTheValuesEvenly() {
        for (int seed = 0; seed < 50; seed++) {
            List<Long> drawn = draw(new WiredReferencePairing(List.of(10L, 20L), new Random(seed)), 5);
            long tens = drawn.stream().filter(value -> value == 10L).count();
            assertTrue(tens == 2 || tens == 3, "five destinations over two values: two and three, got " + drawn);
        }
    }

    @Test
    void thePairingIsDrawnAnewEveryRun() {
        Set<List<Long>> seen = new HashSet<>();
        for (int seed = 0; seed < 50; seed++) {
            seen.add(draw(new WiredReferencePairing(List.of(1L, 2L, 3L), new Random(seed)), 3));
        }
        assertNotEquals(1, seen.size(), "the order is not always the holders' own");
    }

    @Test
    void oneHolderGivesItsValueToEveryDestination() {
        assertEquals(List.of(7L, 7L, 7L), draw(new WiredReferencePairing(List.of(7L)), 3));
    }

    @Test
    void noHolderGivesNothing() {
        WiredReferencePairing pairing = new WiredReferencePairing(List.of());
        assertTrue(pairing.isEmpty());
        assertNull(pairing.next());
    }
}
