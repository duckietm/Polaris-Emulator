package com.eu.habbo.messages.incoming.friends;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Only text passes the word filter; a message of type 1, 2 or 3 reached the friend unfiltered and stayed in
 * the history, with whatever metadata it carried.
 */
class MessengerClientMessageTypesTest {

    @Test
    void textAndHabbiconsWithNoMetadataAreAccepted() {
        assertTrue(SendMessengerMessageEvent.isClientMessage(0, ""));
        assertTrue(SendMessengerMessageEvent.isClientMessage(4, ""));
        assertTrue(SendMessengerMessageEvent.isClientMessage(0, null));
    }

    @Test
    void otherTypesAndAnyMetadataAreRefused() {
        for (int type : new int[] {1, 2, 3, 5, -1}) {
            assertFalse(SendMessengerMessageEvent.isClientMessage(type, ""), "type " + type);
        }
        assertFalse(SendMessengerMessageEvent.isClientMessage(0, "{\"x\":1}"));
    }
}
