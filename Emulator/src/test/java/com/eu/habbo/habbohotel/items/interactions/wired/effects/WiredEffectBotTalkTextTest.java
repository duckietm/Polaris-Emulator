package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** Bot Talk's "bot TAB message" is split the way Habbo splits it. */
class WiredEffectBotTalkTextTest {

    @Test
    void aBotNameWithNoMessageIsReadAsAMessageWithNoBot() {
        assertArrayEquals(new String[] {"", "Robbie"}, WiredEffectBotTalk.splitTalkText("Robbie\t"));
        assertArrayEquals(new String[] {"", "Robbie"}, WiredEffectBotTalk.splitTalkText("Robbie"));
        assertArrayEquals(new String[] {"", "Robbie"}, WiredEffectBotTalk.splitTalkText("\tRobbie"));
        assertArrayEquals(new String[] {"", ""}, WiredEffectBotTalk.splitTalkText("\t"));
    }

    @Test
    void aBotAndAMessageKeepFurtherTabsInTheMessage() {
        assertArrayEquals(new String[] {"Alice", "Hello"}, WiredEffectBotTalk.splitTalkText("Alice\tHello"));
        assertArrayEquals(new String[] {"Alice", "Hi\tthere"}, WiredEffectBotTalk.splitTalkText("Alice\tHi\tthere\t"));
    }
}
