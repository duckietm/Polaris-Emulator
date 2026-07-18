package com.eu.habbo.messages.incoming.inventory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryRequestRateLimitContractTest {
    @Test
    void inRoomAndHotelViewRequestsUseDistinctRateLimitKeys() throws Exception {
        String registry = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/PacketManager.java"));

        assertTrue(registry.contains(
                        "this.registerHandler(Incoming.RequestInventoryItemsEvent, RequestInventoryItemsEvent.class);"),
                "in-room inventory requests must use their dedicated handler");
        assertTrue(registry.contains(
                        "this.registerHandler(Incoming.HotelViewInventoryEvent, HotelViewInventoryEvent.class);"),
                "hotel-view inventory requests must not share the in-room handler rate-limit key");
    }
}
