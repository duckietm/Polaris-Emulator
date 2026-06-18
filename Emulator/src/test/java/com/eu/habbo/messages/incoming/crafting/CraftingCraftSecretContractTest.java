package com.eu.habbo.messages.incoming.crafting;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftingCraftSecretContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/crafting/CraftingCraftSecretEvent.java"));
    }

    @Test
    void rejectsInvalidIngredientCountsBeforeReadingItemIds() throws Exception {
        String source = source();

        int countRead = source.indexOf("int count = this.packet.readInt()");
        int guard = source.indexOf("count <= 0 || count > MAX_SECRET_CRAFT_INGREDIENTS", countRead);
        int loop = source.indexOf("for (int i = 0; i < count; i++)", guard);

        assertTrue(countRead > -1, "secret crafting must read the client supplied ingredient count");
        assertTrue(guard > countRead, "secret crafting must validate the ingredient count");
        assertTrue(loop > guard, "ingredient count validation must happen before item id reads");
    }

    @Test
    void rejectsDuplicateIngredientItemsBeforeRecipeLookup() throws Exception {
        String source = source();

        int setDeclaration = source.indexOf("Set<HabboItem> habboItems = new THashSet<>()");
        int duplicateGuard = source.indexOf("habboItem == null || !habboItems.add(habboItem)", setDeclaration);
        int recipeLookup = source.indexOf("CraftingRecipe recipe = altar.getRecipe(items)", duplicateGuard);

        assertTrue(setDeclaration > -1, "secret crafting should track unique inventory items");
        assertTrue(duplicateGuard > setDeclaration, "secret crafting must reject duplicate item ids");
        assertTrue(recipeLookup > duplicateGuard, "duplicate rejection must happen before recipe lookup/reward creation");
    }
}
