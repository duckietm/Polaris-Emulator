package com.eu.habbo.messages.incoming.rooms.pets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoomUserGiveHandItemPetEventTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theHeaderMatchesTheRendererAndIsRegistered() throws Exception {
        assertTrue(
                source("com/eu/habbo/messages/incoming/Incoming.java").contains("RoomUserGiveHandItemPetEvent = 2768"));
        assertTrue(source("com/eu/habbo/messages/PacketManager.java")
                .contains("Incoming.RoomUserGiveHandItemPetEvent, RoomUserGiveHandItemPetEvent.class"));
    }

    @Test
    void itWalksToThePetAndRefusesAnEmptyHandOrABlockedRoom() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/rooms/pets/RoomUserGiveHandItemPetEvent.java");

        assertTrue(handler.contains("RoomHanditemBlockSupport.isHanditemBlocked(room)"));
        assertTrue(handler.contains("habbo.getRoomUnit().getHandItem() <= 0"));
        assertTrue(handler.contains("new HabboGiveHandItemToPet(habbo, pet)"));
        assertTrue(handler.contains("new RoomUnitWalkToRoomUnit("));
    }

    @Test
    void thePetTakesTheItemAndTheHandEmpties() throws Exception {
        String runnable = source("com/eu/habbo/threading/runnables/HabboGiveHandItemToPet.java");

        assertTrue(runnable.contains("this.from.getRoomUnit().setHandItem(0);"));
        assertTrue(runnable.contains("new RoomUserHandItemComposer(this.from.getRoomUnit())"));
        assertTrue(runnable.contains("this.pet.addHappiness(HAPPINESS);"));
        assertTrue(runnable.contains("new PetStatusUpdateComposer(this.pet)"));
    }
}
