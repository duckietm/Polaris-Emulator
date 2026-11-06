package polaris.messages.incoming.rooms.items;

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
        assertTrue(source("polaris/messages/incoming/Incoming.java").contains("OpenMysteryTrophyEvent = 3074"));
        assertTrue(source("polaris/messages/PacketManager.java")
                .contains("Incoming.OpenMysteryTrophyEvent, OpenMysteryTrophyEvent.class"));
    }

    @Test
    void onlyTheOwnerEngravesAndOnlyOnce() throws Exception {
        String handler = source("polaris/messages/incoming/rooms/items/OpenMysteryTrophyEvent.java");

        assertTrue(handler.contains("item.getUserId() != user.getUserInfo().getId()"));
        assertTrue(handler.contains("getInteractionType().getType() != InteractionTrophy.class"));
        assertTrue(handler.contains("!item.getExtradata().isEmpty()"));
    }

    @Test
    void theTextIsWrittenLikeACatalogTrophy() throws Exception {
        String handler = source("polaris/messages/incoming/rooms/items/OpenMysteryTrophyEvent.java");

        assertTrue(handler.contains("prepareFurnitureExtraData(user, item.getBaseItem(), text)"));
        assertTrue(source("polaris/hotel/catalog/CatalogManager.java")
                .contains("public String prepareFurnitureExtraData(User user, Item baseItem, String extraData) {"));
    }
}
