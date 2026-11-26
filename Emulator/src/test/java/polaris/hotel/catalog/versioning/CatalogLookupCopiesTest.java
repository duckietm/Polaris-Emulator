package polaris.hotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CatalogLookupCopiesTest {
    /** 85,000 ids in five dense blocks, as a large items_base has them. */
    private static Set<Integer> largeCatalogue() {
        Set<Integer> ids = new HashSet<>();
        for (int block : new int[] {1, 100_000, 200_000, 3_000_000, 5_000_000}) {
            for (int offset = 0; offset < 17_000; offset++) ids.add(block + offset);
        }
        return ids;
    }

    @Test
    void aCopyHoldsTheSameIdsAndCannotBeChanged() {
        Set<Integer> copy = CatalogLookupCopies.set(Set.of(1, 2, 3));

        assertEquals(Set.of(1, 2, 3), copy);
        assertThrows(UnsupportedOperationException.class, () -> copy.add(4));
    }

    @Test
    void aCopyIsNotChangedByItsSource() {
        Set<Integer> source = new HashSet<>(Set.of(1, 2));
        Set<Integer> copy = CatalogLookupCopies.set(source);
        source.add(3);

        assertEquals(Set.of(1, 2), copy);
    }

    @Test
    void nullsAreRefusedAsCopyOfDid() {
        Set<Integer> withNull = new HashSet<>();
        withNull.add(null);
        Map<Integer, Integer> nullValue = new HashMap<>();
        nullValue.put(1, null);

        assertThrows(NullPointerException.class, () -> CatalogLookupCopies.set(withNull));
        assertThrows(NullPointerException.class, () -> CatalogLookupCopies.map(nullValue));
    }

    @Test
    void aLargeCatalogueIsCopiedAndLookedUpQuickly() {
        Set<Integer> ids = largeCatalogue();
        Map<Integer, Integer> limited = new HashMap<>();
        for (Integer id : ids) limited.put(id, id % 7);

        long startedAt = System.nanoTime();
        Set<Integer> idCopy = CatalogLookupCopies.set(ids);
        Map<Integer, Integer> limitedCopy = CatalogLookupCopies.map(limited);
        int found = 0;
        for (Integer id : ids) {
            if (idCopy.contains(id) && limitedCopy.containsKey(id)) found++;
        }
        long tookMs = (System.nanoTime() - startedAt) / 1_000_000;

        assertEquals(ids.size(), found);
        // Set.copyOf and Map.copyOf took 500 to 2,700 ms here; a generous bound for a slow test machine.
        assertTrue(tookMs < 400, "took " + tookMs + " ms");
    }
}
