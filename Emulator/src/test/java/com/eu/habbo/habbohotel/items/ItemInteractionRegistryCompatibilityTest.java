package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItemInteractionRegistryCompatibilityTest {

    @Test
    void lookupFallbackOrderingAndUniquenessStayStable() {
        TestItemManager manager = new TestItemManager();
        manager.loadDefaults();

        ItemInteraction defaultInteraction = manager.getItemInteraction(InteractionDefault.class);
        assertSame(defaultInteraction, manager.getItemInteraction("missing-interaction"));

        List<String> names = manager.getInteractionList();
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String::compareTo);
        assertEquals(sorted, names);
        assertTrue(names.contains("default"));

        assertThrows(
                RuntimeException.class,
                () -> manager.addItemInteraction(
                        new ItemInteraction("duplicate-default-type", InteractionDefault.class)));
    }

    private static final class TestItemManager extends ItemManager {
        private void loadDefaults() {
            loadItemInteractions();
        }
    }
}
