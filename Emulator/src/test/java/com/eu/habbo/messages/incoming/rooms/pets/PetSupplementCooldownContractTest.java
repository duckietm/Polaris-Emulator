package com.eu.habbo.messages.incoming.rooms.pets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A monster plant supplement renewed the plant's whole life on every packet, so it never died. */
class PetSupplementCooldownContractTest {

    @Test
    void aSupplementWaitsLikeATreatment() throws Exception {
        String event = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/pets/PetSupplementEvent.java"));
        String plant = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/pets/MonsterplantPet.java"));

        int gate = event.indexOf("if (!plant.maySupplement()) {");
        assertTrue(gate > 0 && gate < event.indexOf("plant.setDeathTimestamp("));
        assertTrue(plant.contains("public boolean maySupplement() {\n        return this.mayScratch();")
                || plant.contains("public boolean maySupplement() {\r\n        return this.mayScratch();"));
    }
}
