package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredFairDealTest {
    @Test
    void everyValueIsUsedKTimesBeforeAnyIsUsedKPlusOneTimes() {
        List<String> values = List.of("a", "b", "c");

        for (int count = 0; count <= 10; count++) {
            List<String> dealt = WiredFairDeal.deal(values, count, new Random(count));
            assertEquals(count, dealt.size());

            for (int prefix = 1; prefix <= dealt.size(); prefix++) {
                Map<String, Integer> uses = new HashMap<>();
                dealt.subList(0, prefix).forEach(value -> uses.merge(value, 1, Integer::sum));
                int min = values.stream()
                        .mapToInt(value -> uses.getOrDefault(value, 0))
                        .min()
                        .orElse(0);
                int max = values.stream()
                        .mapToInt(value -> uses.getOrDefault(value, 0))
                        .max()
                        .orElse(0);
                assertTrue(max - min <= 1, "prefix " + prefix + " of " + dealt);
            }
        }
    }

    @Test
    void asManyValuesAsSlotsAreAllDistinctAndTheExtraGoesToDifferentValues() {
        List<Integer> users = List.of(1, 2);
        Set<Integer> extra = new HashSet<>();

        for (int seed = 0; seed < 40; seed++) {
            List<Integer> two = WiredFairDeal.deal(users, 2, new Random(seed));
            assertEquals(Set.of(1, 2), Set.copyOf(two));
            extra.add(WiredFairDeal.deal(users, 3, new Random(seed)).get(2));
        }

        assertEquals(Set.of(1, 2), extra);
    }

    @Test
    void nothingToDealGivesAnEmptyList() {
        assertTrue(WiredFairDeal.deal(List.of(), 3).isEmpty());
        assertTrue(WiredFairDeal.deal(null, 3).isEmpty());
        assertTrue(WiredFairDeal.deal(List.of(1), 0).isEmpty());
    }
}
