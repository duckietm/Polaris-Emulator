package com.eu.habbo.habbohotel.users.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Crafting and the wired take-furni effect rely on a null when the inventory holds no item of the base item. */
class ItemsComponentTakeMissingContractTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/habbohotel/users/inventory/ItemsComponent.java");

    @Test
    void aMissingItemIsReportedAsNullBeforeAnythingIsRemoved() throws Exception {
        String source = Files.readString(SOURCE);
        int method = source.indexOf("public HabboItem getAndRemoveHabboItem(final Item item)");
        int nullReturn = source.indexOf("if (found == null) {", method);
        int remove = source.indexOf("this.removeHabboItem(found);", method);

        assertTrue(method > 0);
        assertTrue(nullReturn > method && nullReturn < remove, "the null return comes before the removal");
    }
}
