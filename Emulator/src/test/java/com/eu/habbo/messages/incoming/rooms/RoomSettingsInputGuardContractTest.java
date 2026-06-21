package com.eu.habbo.messages.incoming.rooms;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomSettingsInputGuardContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/RoomSettingsSaveEvent.java"));
    }

    @Test
    void roomStateAndTagCountAreValidatedWithoutModuloOrTruncation() throws Exception {
        String source = source();

        int stateRead = source.indexOf("int stateId = this.packet.readInt()");
        int stateGuard = source.indexOf("stateId < 0 || stateId >= RoomState.values().length", stateRead);
        int stateAssign = source.indexOf("RoomState state = RoomState.values()[stateId]", stateGuard);
        int tagCount = source.indexOf("int count = this.packet.readInt()", stateAssign);
        int tagGuard = source.indexOf("count < 0 || count > MAX_TAGS", tagCount);
        int tagLoop = source.indexOf("for (int i = 0; i < count; i++)", tagGuard);

        assertTrue(source.contains("MAX_TAGS = 2"), "Room settings tag count should have an explicit cap");
        assertTrue(!source.contains("% RoomState.values().length"),
                "Room state must not use modulo because negative values can crash or remap input");
        assertTrue(!source.contains("Math.min(this.packet.readInt(), 2)"),
                "Room settings must reject oversized tag counts instead of truncating and desynchronizing the packet");
        assertTrue(stateRead > -1 && stateGuard > stateRead && stateAssign > stateGuard,
                "Room state must be range-checked before indexing RoomState.values()");
        assertTrue(tagCount > -1 && tagGuard > tagCount && tagLoop > tagGuard,
                "Tag count must be range-checked before reading tag strings");
    }

    @Test
    void roomSettingsOptionsAreValidatedBeforeMutatingRoom() throws Exception {
        String source = source();

        int tradeMode = source.indexOf("int tradeMode = this.packet.readInt()");
        int validation = source.indexOf("!isInRange(tradeMode, 0, MAX_OPTION_LEVEL)", tradeMode);
        int setTags = source.indexOf("room.setTags(tags.toString())", validation);
        int setChatDistance = source.indexOf("room.setChatDistance(chatDistance)", setTags);

        assertTrue(source.contains("MAX_ROOM_PASSWORD_LENGTH = 64"),
                "Room password should have a bounded server-side length");
        assertTrue(source.contains("MAX_USERS_MAX = 200"),
                "Room capacity should have a bounded server-side maximum");
        assertTrue(source.contains("MIN_CHAT_DISTANCE = 1") && source.contains("MAX_CHAT_DISTANCE = 99"),
                "Room chat distance should be explicitly bounded");
        assertTrue(!source.contains("Math.abs(this.packet.readInt())"),
                "Room settings must reject invalid chat distance instead of converting negative values");
        assertTrue(validation > tradeMode, "Room options must be validated after reading them");
        assertTrue(validation < setTags, "Room options must be validated before mutating room fields");
        assertTrue(setChatDistance > setTags, "Validated chat distance should be applied after the guard block");
        assertTrue(source.contains("private static boolean isInRange"),
                "Room settings should use one clear range helper for numeric option guards");
    }
}
