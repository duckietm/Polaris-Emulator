package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomPickupOwnershipContractTest {
    @Test
    void pickupReturnsItemToPickerWhenPickerOwnsTheItem() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/Room.java"));

        assertTrue(source.contains("picker.getHabboInfo().getId() == item.getUserId()"),
                "Room.pickUpItem should compare the picker id against the item owner id");
        assertFalse(source.contains("picker.getHabboInfo().getId() == item.getId()"),
                "Room.pickUpItem must not compare user ids against furniture item ids");
    }
}
