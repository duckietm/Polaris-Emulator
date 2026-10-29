package com.eu.habbo.habbohotel.games.battlebanzai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The fill recursed once per tile, scanned every game tile for each step and read the live locked
 * tiles from the fill thread: a large field overflowed the stack or ran for seconds, and a tile locked
 * meanwhile threw ConcurrentModificationException. It now walks a snapshot iteratively.
 */
class BattleBanzaiAreaFillTest {

    private static Set<Integer> square(int size) {
        Set<Integer> field = new HashSet<>();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                field.add(BattleBanzaiAreaFill.key(x, y));
            }
        }
        return field;
    }

    private static Set<Integer> border(int size) {
        Set<Integer> locked = new HashSet<>();
        for (int i = 0; i < size; i++) {
            locked.add(BattleBanzaiAreaFill.key(i, 0));
            locked.add(BattleBanzaiAreaFill.key(i, size - 1));
            locked.add(BattleBanzaiAreaFill.key(0, i));
            locked.add(BattleBanzaiAreaFill.key(size - 1, i));
        }
        return locked;
    }

    @Test
    void anAreaClosedByTheTeamIsFilled() {
        List<Integer> area = BattleBanzaiAreaFill.enclosedArea(2, 2, square(5), border(5), Set.of());

        assertNotNull(area);
        assertEquals(9, area.size());
        assertTrue(area.contains(BattleBanzaiAreaFill.key(1, 1)));
        assertTrue(area.contains(BattleBanzaiAreaFill.key(3, 3)));
    }

    @Test
    void anAreaThatReachesTheEdgeIsNotFilled() {
        Set<Integer> locked = border(5);
        locked.remove(BattleBanzaiAreaFill.key(4, 2));

        assertNull(BattleBanzaiAreaFill.enclosedArea(2, 2, square(5), locked, Set.of()));
    }

    @Test
    void anAreaTouchingAnotherTeamsTileIsNotFilled() {
        Set<Integer> foreign = Set.of(BattleBanzaiAreaFill.key(2, 2));

        assertNull(BattleBanzaiAreaFill.enclosedArea(1, 1, square(5), border(5), foreign));
    }

    @Test
    void startingOnAnOwnTileFillsNothing() {
        List<Integer> area = BattleBanzaiAreaFill.enclosedArea(0, 0, square(5), border(5), Set.of());

        assertNotNull(area);
        assertTrue(area.isEmpty());
    }

    @Test
    void aLargeFieldIsFilledWithoutRecursion() {
        int size = 400;
        List<Integer> area = BattleBanzaiAreaFill.enclosedArea(1, 1, square(size), border(size), Set.of());

        assertNotNull(area);
        assertEquals((size - 2) * (size - 2), area.size());
    }
}
