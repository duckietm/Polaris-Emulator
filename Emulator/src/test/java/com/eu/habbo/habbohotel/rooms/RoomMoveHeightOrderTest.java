package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A move with an explicit height turned the furni first and then refused the height, leaving it turned. */
class RoomMoveHeightOrderTest {

    @Test
    void theHeightIsCheckedBeforeTheFurniTurns() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/rooms/RoomItemMovementService.java"));
        int heightCheck = source.indexOf("// Height sanity checks, before the item is turned");
        int turn = source.indexOf("item.setRotation(rotation);", heightCheck);
        int previousTurn = source.lastIndexOf("item.setRotation(rotation);", heightCheck);
        int methodStart = source.lastIndexOf("int oldRotation = item.getRotation();", heightCheck);

        assertTrue(heightCheck > -1 && turn > heightCheck);
        assertTrue(previousTurn < methodStart || methodStart == -1, "no turn between the method start and the check");
    }
}
