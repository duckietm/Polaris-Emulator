package com.eu.habbo.messages.incoming.rooms.pets;

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
    private static final Path INCOMING = Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms");

    @Test
    void everyUseClaimsTheFurniFirst() throws Exception {
        String pets = Files.readString(INCOMING.resolve("pets/PetUseItemEvent.java"));
        String clothing = Files.readString(INCOMING.resolve("items/RedeemClothingEvent.java"));

        assertTrue(
                pets.split("if \\(!room\\.claimItem\\(item\\)\\) return;", -1).length - 1 >= 4,
                "every plant product and the horse item");
        assertFalse(pets.contains("room.removeHabboItem(item);"));
        assertTrue(clothing.indexOf("room.claimItem(item)") < clothing.indexOf("this.grantClothing(clothing.id)"));
        assertTrue(clothing.contains("room.addHabboItem(item);"), "a refused grant puts the furni back");
    }
}
