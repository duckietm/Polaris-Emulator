package com.eu.habbo.messages.incoming.rooms.items;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomPickupChooserContractTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/RoomPickupChooserEvent.java"));
    }

    @Test
    void rejectsInvalidBatchSizesBeforeReadingItemIds() throws Exception {
        String source = source();

        int countRead = source.indexOf("int count = this.packet.readInt()");
        int guard = source.indexOf("count <= 0 || count > MAX_PICKUP_CHOOSER_ITEMS", countRead);
        int loop = source.indexOf("for (int i = 0; i < count; i++)", countRead);

        assertTrue(countRead > -1, "Pickup chooser must read the client-provided count");
        assertTrue(guard > countRead, "Pickup chooser must reject invalid batch sizes");
        assertTrue(guard < loop, "Batch size validation must happen before consuming item ids");
    }

    @Test
    void chooserSkipsPostItsLikeSinglePickup() throws Exception {
        String source = source();

        assertTrue(source.contains("import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;"));
        assertTrue(source.contains("item instanceof InteractionPostIt"));
        assertTrue(source.contains("continue;"));
    }
}
