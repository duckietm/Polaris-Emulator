package com.eu.habbo.messages.incoming.rooms.pets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The compost of a dead monster plant was not null-checked and not saved where it was placed. */
class CompostMonsterplantContractTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/pets/CompostMonsterplantEvent.java");

    @Test
    void theCompostIsCheckedAndSavedInTheRoom() throws Exception {
        String source = Files.readString(SOURCE);
        int nullCheck = source.indexOf("if (compost == null) return;");
        int roomId = source.indexOf("compost.setRoomId(room.getId());");
        int save = source.indexOf("compost.needsUpdate(true);");
        int add = source.indexOf("room.addHabboItem(compost);");
        int removePet = source.indexOf("pet.removeFromRoom();");

        assertTrue(nullCheck > 0 && nullCheck < roomId && roomId < add && save < add);
        assertTrue(nullCheck < removePet, "a failed compost leaves the plant");
    }
}
