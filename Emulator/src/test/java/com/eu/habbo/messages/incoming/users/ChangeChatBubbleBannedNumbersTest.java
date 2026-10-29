package com.eu.habbo.messages.incoming.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * commands.cmd_chatcolor.banned_numbers was read with Integer.parseInt on every entry: a blank setting, a space
 * after a ';' or a trailing ';' threw on every bubble change, so no user without the permission could change it.
 */
class ChangeChatBubbleBannedNumbersTest {

    @Test
    void listedBubblesAreBanned() {
        assertTrue(ChangeChatBubbleEvent.isBanned("1;23;37", 23));
        assertFalse(ChangeChatBubbleEvent.isBanned("1;23;37", 5));
    }

    @Test
    void blanksSpacesAndWordsAreSkipped() {
        assertTrue(ChangeChatBubbleEvent.isBanned(" 2 ; ;x;31;", 31));
        assertTrue(ChangeChatBubbleEvent.isBanned(" 2 ; ;x;31;", 2));
        assertFalse(ChangeChatBubbleEvent.isBanned("", 0));
        assertFalse(ChangeChatBubbleEvent.isBanned(null, 0));
        assertFalse(ChangeChatBubbleEvent.isBanned(";;", 0));
    }
}
