package com.eu.habbo.messages.incoming.rooms.pets;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PetUseItemOwnershipContractTest {
    @Test
    void validatesItemAndPetOwnershipBeforeHorseMutations() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/pets/PetUseItemEvent.java"));

        int petLookup = source.indexOf("Pet pet = this.client.getHabbo().getHabboInfo().getCurrentRoom().getPet(petId)");
        int ownershipGuard = source.indexOf("item.getUserId() != this.client.getHabbo().getHabboInfo().getId()", petLookup);
        int petGuard = source.indexOf("pet.getUserId() != this.client.getHabbo().getHabboInfo().getId()", ownershipGuard);
        int horseBranch = source.indexOf("if (pet instanceof HorsePet)", petGuard);

        assertTrue(petLookup > -1, "PetUseItemEvent should resolve the target pet");
        assertTrue(ownershipGuard > petLookup, "PetUseItemEvent must require ownership of the consumable item");
        assertTrue(petGuard > ownershipGuard, "PetUseItemEvent must require ownership of the target pet");
        assertTrue(horseBranch > petGuard, "Ownership checks must run before horse item mutations");
    }
}
