package polaris.messages.incoming.rooms.pets;

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
        assertTrue(source("polaris/messages/incoming/Incoming.java").contains("RoomUserGiveHandItemPetEvent = 2768"));
        assertTrue(source("polaris/messages/PacketManager.java")
                .contains("Incoming.RoomUserGiveHandItemPetEvent, RoomUserGiveHandItemPetEvent.class"));
    }

    @Test
    void itWalksToThePetAndRefusesAnEmptyHandOrABlockedRoom() throws Exception {
        String handler = source("polaris/messages/incoming/rooms/pets/RoomUserGiveHandItemPetEvent.java");

        assertTrue(handler.contains("RoomHanditemBlockSupport.isHanditemBlocked(room)"));
        assertTrue(handler.contains("user.getRoomUnit().getHandItem() <= 0"));
        assertTrue(handler.contains("new UserGiveHandItemToPet(user, pet)"));
        assertTrue(handler.contains("new RoomUnitWalkToRoomUnit("));
    }

    @Test
    void thePetTakesTheItemAndTheHandEmpties() throws Exception {
        String runnable = source("polaris/threading/runnables/UserGiveHandItemToPet.java");

        assertTrue(runnable.contains("this.from.getRoomUnit().setHandItem(0);"));
        assertTrue(runnable.contains("new RoomUserHandItemComposer(this.from.getRoomUnit())"));
        assertTrue(runnable.contains("this.pet.addHappiness(HAPPINESS);"));
        assertTrue(runnable.contains("new PetStatusUpdateComposer(this.pet)"));
    }
}
