package com.eu.habbo.messages.outgoing.modtool;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ModToolTimestampFormattingTest {
    private static final int TIMESTAMP = 1_700_000_000;

    @Test
    void preservesChatlogAndUserInfoTimestampText() {
        assertEquals(
                format("HH:mm", TIMESTAMP),
                ModToolUserChatlogComposer.formatTimestamp(TIMESTAMP));
        assertEquals(
                format("HH:mm", TIMESTAMP),
                ModToolIssueChatlogComposer.formatTimestamp(TIMESTAMP));
        assertEquals(
                format("yyyy-MM-dd HH:mm", TIMESTAMP),
                ModToolUserInfoComposer.formatUnixTimestamp(TIMESTAMP));
        assertEquals("", ModToolUserInfoComposer.formatUnixTimestamp(0));
    }

    @Test
    void publicChatlogFormattersRemainDirectlyWritable() {
        SimpleDateFormat userFormatter = ModToolUserChatlogComposer.format;
        SimpleDateFormat issueFormatter = ModToolIssueChatlogComposer.format;
        try {
            SimpleDateFormat replacement = new SimpleDateFormat("ss");
            ModToolUserChatlogComposer.format = replacement;
            ModToolIssueChatlogComposer.format = replacement;

            assertSame(replacement, ModToolUserChatlogComposer.format);
            assertSame(replacement, ModToolIssueChatlogComposer.format);
        } finally {
            ModToolUserChatlogComposer.format = userFormatter;
            ModToolIssueChatlogComposer.format = issueFormatter;
        }
    }

    private static String format(String pattern, int timestamp) {
        return new SimpleDateFormat(pattern).format(new Date(timestamp * 1000L));
    }
}
