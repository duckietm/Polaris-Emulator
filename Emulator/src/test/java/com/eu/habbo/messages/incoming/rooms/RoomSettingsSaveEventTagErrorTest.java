package com.eu.habbo.messages.incoming.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoomSettingsSaveEventTagErrorTest {
    @Test
    void namesTheFirstTagThePredicateRejects() {
        assertEquals(
                "staffroom",
                RoomSettingsSaveEvent.offendingTag(List.of("fun", "staffroom"), tag -> tag.contains("staff")));
    }

    @Test
    void isEmptyWhenNoSingleTagIsRejected() {
        assertEquals("", RoomSettingsSaveEvent.offendingTag(List.of("fun", "games"), tag -> tag.contains("staff")));
        assertEquals("", RoomSettingsSaveEvent.offendingTag(List.of(), tag -> true));
    }

    @Test
    void aTagCannotHoldTheSeparatorOrAControlCharacter() {
        assertTrue(RoomSettingsSaveEvent.isAcceptableTag("music"));
        assertFalse(RoomSettingsSaveEvent.isAcceptableTag("a;b;c;d"));
        assertFalse(RoomSettingsSaveEvent.isAcceptableTag("line
break"));
        assertFalse(RoomSettingsSaveEvent.isAcceptableTag(null));
    }
}
