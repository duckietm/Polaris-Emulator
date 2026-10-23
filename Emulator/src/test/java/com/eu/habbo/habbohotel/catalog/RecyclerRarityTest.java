package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The recycler rarity draw: levels 6 (mythic) down to 2 are tried in turn with a 1 in N chance each. */
class RecyclerRarityTest {
    private static final Map<Integer, Integer> CHANCES = Map.of(6, 5000, 5, 400, 4, 40, 3, 10, 2, 4);

    /** A "random" that hits (returns chance - 1) only for the given chance, and records what it was asked. */
    private static int draw(Map<Integer, Integer> chances, int hitChance, List<Integer> asked) {
        return CatalogManager.drawRecyclerRarity(level -> chances.getOrDefault(level, 0), bound -> {
            asked.add(bound);
            return bound == hitChance ? bound - 1 : 0;
        });
    }

    @Test
    void theMythicLevelIsTriedFirst() {
        List<Integer> asked = new ArrayList<>();

        assertEquals(6, draw(CHANCES, 5000, asked));
        assertEquals(List.of(5000), asked);
    }

    @Test
    void eachLevelIsTriedFromTheRarestDown() {
        List<Integer> asked = new ArrayList<>();

        assertEquals(3, draw(CHANCES, 10, asked));
        assertEquals(List.of(5000, 400, 40, 10), asked);
    }

    @Test
    void whenNoLevelHitsThePrizeIsCommon() {
        List<Integer> asked = new ArrayList<>();

        assertEquals(1, draw(CHANCES, -1, asked));
        assertEquals(List.of(5000, 400, 40, 10, 4), asked);
    }

    @Test
    void aLevelWithoutAChanceIsSkippedInsteadOfFailing() {
        List<Integer> asked = new ArrayList<>();

        // An older database without hotel.ecotron.rarity.chance.6: the draw behaves as the five-level wheel did.
        assertEquals(5, draw(Map.of(5, 400, 4, 40, 3, 10, 2, 4), 400, asked));
        assertEquals(List.of(400), asked);
    }
}
