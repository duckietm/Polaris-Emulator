package polaris.messages.incoming.rooms.pets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A plant product, a horse item or a clothing furni was used and then taken out of the room: a pickup in between
 * left it in an inventory although it had been used, so it could be used again.
 */
class ConsumedFurniClaimTest {
    private static final Path INCOMING = Path.of("src/main/java/polaris/messages/incoming/rooms");

    @Test
    void everyUseClaimsTheFurniFirst() throws Exception {
        String pets = Files.readString(INCOMING.resolve("pets/PetUseItemEvent.java"));
        String clothing = Files.readString(INCOMING.resolve("items/RedeemClothingEvent.java"));

        assertTrue(
                pets.split("if \\(!this\\.claim\\(room, item\\)\\) return;", -1).length - 1 >= 4,
                "every plant product and the horse item");
        // The claim takes a placed product out of the room and one used from the inventory out of the inventory.
        assertTrue(pets.contains(": room.claimItem(item);"), "a product in the room is claimed from the room");
        assertTrue(
                pets.contains("tryRemoveUserItem(item)"), "a product in the inventory is claimed from the inventory");
        assertFalse(pets.contains("room.removeUserItem(item);"));
        assertTrue(clothing.indexOf("room.claimItem(item)") < clothing.indexOf("this.grantClothing(clothing.id)"));
        assertTrue(clothing.contains("room.addUserItem(item);"), "a refused grant puts the furni back");
    }
}
