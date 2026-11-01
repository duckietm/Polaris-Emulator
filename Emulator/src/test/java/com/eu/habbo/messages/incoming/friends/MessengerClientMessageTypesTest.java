package com.eu.habbo.messages.incoming.friends;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Only text passes the word filter; a message of type 1, 2 or 3 reached the friend unfiltered and stayed in the
 * history. Text keeps its metadata: the protocol carries it back to the recipient (the renderer's messenger e2e).
 */
class MessengerClientMessageTypesTest {

    @Test
    void textAndHabbiconsAreAccepted() {
        assertTrue(SendMessengerMessageEvent.isClientMessage(0));
        assertTrue(SendMessengerMessageEvent.isClientMessage(4));
    }

    @Test
    void otherTypesAreRefused() {
        for (int type : new int[] {1, 2, 3, 5, -1}) {
            assertFalse(SendMessengerMessageEvent.isClientMessage(type), "type " + type);
        }
    }
}
