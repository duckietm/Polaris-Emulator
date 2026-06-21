package com.eu.habbo.messages.incoming.rooms.pets;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PetPackageNameContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/pets/PetPackageNameEvent.java"));
    }

    @Test
    void validatesItemIdBeforeRoomItemLookup() throws Exception {
        String source = source();

        int itemIdRead = source.indexOf("int itemId = this.packet.readInt()");
        int idGuard = source.indexOf("RoomItemInputGuard.isPositiveId(itemId)", itemIdRead);
        int lookup = source.indexOf("HabboItem item = room.getHabboItem(itemId)", idGuard);

        assertTrue(itemIdRead > -1, "pet package handler should read an item id");
        assertTrue(idGuard > itemIdRead, "pet package handler should reject invalid item ids");
        assertTrue(lookup > idGuard, "item id validation must happen before room item lookup");
    }

    @Test
    void validatesNameAndTileBeforeCreatingPet() throws Exception {
        String source = source();

        int nameValidation = source.indexOf("int nameError = validatePetPackageName(name)");
        int tileLookup = source.indexOf("RoomTile tile = room.getLayout().getTile", nameValidation);
        int createPet = source.indexOf("createPet", tileLookup);

        assertTrue(nameValidation > -1, "pet package handler should validate package pet names");
        assertTrue(tileLookup > nameValidation, "tile lookup should happen after name validation");
        assertTrue(createPet > tileLookup, "pet creation must happen after validating the target tile");
    }

    @Test
    void packageNamesUseNormalPetNamePolicy() throws Exception {
        String source = source();

        assertTrue(source.contains("CheckPetNameEvent.PET_NAME_LENGTH_MINIMUM"),
                "package pet names should use the configured minimum pet-name length");
        assertTrue(source.contains("CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM"),
                "package pet names should use the configured maximum pet-name length");
        assertTrue(source.contains("StringUtils.isAlphanumeric(name)"),
                "package pet names should use alphanumeric validation");
    }
}
