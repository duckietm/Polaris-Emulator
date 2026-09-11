package com.eu.habbo.messages.incoming.rooms.items;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenMysteryTrophyEventTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theHeaderMatchesTheRendererAndIsRegistered() throws Exception {
        assertTrue(source("com/eu/habbo/messages/incoming/Incoming.java").contains("OpenMysteryTrophyEvent = 3074"));
        assertTrue(source("com/eu/habbo/messages/PacketManager.java")
                .contains("Incoming.OpenMysteryTrophyEvent, OpenMysteryTrophyEvent.class"));
    }

    @Test
    void onlyTheOwnerEngravesAndOnlyOnce() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/rooms/items/OpenMysteryTrophyEvent.java");

        assertTrue(handler.contains("item.getUserId() != habbo.getHabboInfo().getId()"));
        assertTrue(handler.contains("getInteractionType().getType() != InteractionTrophy.class"));
        assertTrue(handler.contains("!item.getExtradata().isEmpty()"));
    }

    @Test
    void theTextIsWrittenLikeACatalogTrophy() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/rooms/items/OpenMysteryTrophyEvent.java");

        assertTrue(handler.contains("prepareFurnitureExtraData(habbo, item.getBaseItem(), text)"));
        assertTrue(source("com/eu/habbo/habbohotel/catalog/CatalogManager.java")
                .contains("public String prepareFurnitureExtraData(Habbo habbo, Item baseItem, String extraData) {"));
    }
}
