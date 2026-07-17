package com.eu.habbo.messages.incoming;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketInputHardeningContractTest {
    @Test
    void jukeboxValidatesCountBeforeAllocatingTheTrackList() throws Exception {
        String source = read("catalog/JukeBoxRequestTrackDataEvent.java");

        int count = source.indexOf("int count = this.packet.readInt()");
        int guard = source.indexOf("PacketGuard.isValidIntList(", count);
        int allocation = source.indexOf("new ArrayList<>(count)", guard);

        assertTrue(count > -1);
        assertTrue(guard > count, "jukebox count must be bounded against packet bytes");
        assertTrue(allocation > guard, "jukebox allocation must happen after count validation");
    }

    @Test
    void craftingListHandlersValidateCountsBeforeReadingItemIds() throws Exception {
        assertCountGuardBeforeLoop("crafting/RequestCraftingRecipesAvailableEvent.java");
        assertCountGuardBeforeLoop("crafting/CraftingCraftSecretEvent.java");
    }

    @Test
    void craftingHandlersCheckRoomAndAltarObjectsBeforeDereference() throws Exception {
        assertNullGuard("crafting/RequestCraftingRecipesEvent.java", "if (room == null)", "room.getHabboItem(");
        assertNullGuard("crafting/RequestCraftingRecipesAvailableEvent.java", "if (item == null)", "item.getBaseItem()");
        assertNullGuard("crafting/CraftingCraftItemEvent.java", "if (altar == null)", "altar.getRecipe(");
        assertNullGuard("crafting/CraftingCraftSecretEvent.java", "if (room == null)", "room.getHabboItem(");
    }

    private static void assertCountGuardBeforeLoop(String relativePath) throws Exception {
        String source = read(relativePath);
        int count = source.indexOf("int count = this.packet.readInt()");
        int guard = source.indexOf("PacketGuard.isValidIntList(", count);
        int loop = source.indexOf("for (int i = 0; i < count; i++)", guard);

        assertTrue(count > -1, relativePath + " must read a count");
        assertTrue(guard > count, relativePath + " must validate the count");
        assertTrue(loop > guard, relativePath + " must validate before repeated reads");
    }

    private static void assertNullGuard(String relativePath, String guardText, String dereferenceText) throws Exception {
        String source = read(relativePath);
        int guard = source.indexOf(guardText);
        int dereference = source.indexOf(dereferenceText, guard);

        assertTrue(guard > -1, relativePath + " must contain " + guardText);
        assertTrue(dereference > guard, relativePath + " must guard before " + dereferenceText);
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming", relativePath));
    }
}
