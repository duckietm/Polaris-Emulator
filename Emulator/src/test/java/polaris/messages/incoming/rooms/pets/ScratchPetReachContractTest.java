package polaris.messages.incoming.rooms.pets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A pet out of reach was scratched anyway, and respect points could go below zero. */
class ScratchPetReachContractTest {

    @Test
    void theScratchHappensOnlyOnArrivalAndChecksThePointsAgain() throws Exception {
        String event =
                Files.readString(Path.of("src/main/java/polaris/messages/incoming/rooms/pets/ScratchPetEvent.java"));

        assertFalse(event.contains("closestTile, room, tasks, tasks)"));
        assertTrue(event.contains("user.getRoomUnit(), closestTile, room, tasks, new ArrayList<>())"));

        int recheck = event.indexOf("user.getUserStats().petRespectPointsToGive <= 0) return;");
        assertTrue(recheck > 0 && recheck < event.indexOf("pet.scratched(user);"));
    }

    @Test
    void theRespectCountNeverGoesBelowZero() throws Exception {
        String pet = Files.readString(Path.of("src/main/java/polaris/hotel/pets/Pet.java"));

        assertTrue(pet.contains(
                "if (user.getUserStats().petRespectPointsToGive > 0) user.getUserStats().petRespectPointsToGive--;"));
    }
}
