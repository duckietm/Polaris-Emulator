package polaris.hotel.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FurnitureTextProviderDeltaTest {

    @Test
    void firstReindexReturnsAllAsDelta() {
        FurnitureTextProvider p = new FurnitureTextProvider(true);
        List<FurnidataEntry> delta =
                p.reindex(List.of(new FurnidataEntry(1, "chair", FurnitureType.FLOOR, "Chair", "Sit")));
        assertEquals(1, delta.size());
        assertEquals("Chair", delta.get(0).name());
    }

    @Test
    void unchangedReindexReturnsEmptyDelta() {
        FurnitureTextProvider p = new FurnitureTextProvider(true);
        List<FurnidataEntry> first = List.of(new FurnidataEntry(1, "chair", FurnitureType.FLOOR, "Chair", "Sit"));
        p.reindex(first);
        List<FurnidataEntry> delta = p.reindex(first);
        assertTrue(delta.isEmpty(), "no change => empty delta");
    }

    @Test
    void changedNameAppearsInDeltaWithSanitizedValue() {
        FurnitureTextProvider p = new FurnitureTextProvider(true);
        p.reindex(List.of(new FurnidataEntry(1, "chair", FurnitureType.FLOOR, "Chair", "Sit")));
        List<FurnidataEntry> delta =
                p.reindex(List.of(new FurnidataEntry(1, "chair", FurnitureType.FLOOR, "New %x%", "Sit")));
        assertEquals(1, delta.size());
        assertFalse(delta.get(0).name().contains("%"), "delta carries the sanitized name");
        assertEquals(1, delta.get(0).id());
        assertEquals(FurnitureType.FLOOR, delta.get(0).type());
    }
}
