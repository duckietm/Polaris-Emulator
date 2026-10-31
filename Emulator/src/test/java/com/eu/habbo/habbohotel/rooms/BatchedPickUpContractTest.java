package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Emptying a room sent every owner an inventory packet and a reload per furni, plus a stack-height update per
 * furni; the eject paths then delivered the same furni a second time.
 */
class BatchedPickUpContractTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomItemOwnershipService.java"));
    }

    @Test
    void aBatchCollectsTheFreedFloorAndTheDeliveries() throws Exception {
        String service = source();

        assertTrue(service.contains("batch.freedTiles.addAll(this.floorArea(item));"));
        assertTrue(service.contains("batch.deliveries.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(item);"));
        assertTrue(service.contains("batch.deliveries.forEach((owner, delivered) -> deliver(owner, delivered, picker));"));
    }

    @Test
    void theEjectPathsDoNotDeliverASecondTime() throws Exception {
        String service = source();

        assertFalse(service.contains("addInventoryItems("));
        assertEquals(1, service.split("new InventoryRefreshComposer\\(\\)", -1).length - 1);
    }
}
