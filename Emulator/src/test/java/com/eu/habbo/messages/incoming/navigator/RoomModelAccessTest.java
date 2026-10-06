package com.eu.habbo.messages.incoming.navigator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RoomModelAccessTest {

    @Test
    void clubModelsNeedHabboClub() {
        assertTrue(RequestCreateRoomEvent.mayUseModel("model_a", false, false, false));
        assertFalse(RequestCreateRoomEvent.mayUseModel("model_g", true, false, false));
        assertTrue(RequestCreateRoomEvent.mayUseModel("model_g", true, true, false));
    }

    @Test
    void publicAndGameLayoutsAreForStaff() {
        assertFalse(RequestCreateRoomEvent.mayUseModel("park_a", false, true, false));
        assertFalse(RequestCreateRoomEvent.mayUseModel("snowstorm_arena_1", false, true, false));
        assertTrue(RequestCreateRoomEvent.mayUseModel("park_a", false, false, true));
        assertTrue(RequestCreateRoomEvent.mayUseModel("model_g", true, false, true));
    }

    @Test
    void theModelIsCheckedBeforeTheRoomIsCreated() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/navigator/RequestCreateRoomEvent.java"));
        int check = source.indexOf("if (!mayUseModel(");
        int create = source.indexOf(".createRoomForHabbo(");

        assertTrue(check > -1 && check < create);
        assertTrue(source.contains("STAFF_MODELS = \"acc_navigator_staff\""));
    }
}
