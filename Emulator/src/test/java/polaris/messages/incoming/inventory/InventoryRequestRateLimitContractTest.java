package polaris.messages.incoming.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InventoryRequestRateLimitContractTest {
    @Test
    void inRoomAndHotelViewRequestsUseDistinctRateLimitKeys() throws Exception {
        String registry = Files.readString(Path.of("src/main/java/polaris/messages/PacketManager.java"));

        assertTrue(
                registry.contains(
                        "this.registerHandler(Incoming.RequestInventoryItemsEvent, RequestInventoryItemsEvent.class);"),
                "in-room inventory requests must use their dedicated handler");
        assertTrue(
                registry.contains(
                        "this.registerHandler(Incoming.HotelViewInventoryEvent, HotelViewInventoryEvent.class);"),
                "hotel-view inventory requests must not share the in-room handler rate-limit key");
    }
}
