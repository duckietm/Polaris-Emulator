package com.eu.habbo.messages.outgoing.modtool;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
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

    @Test
    void firstPartyChatFormattingDoesNotUsePublicMutableFormatters() {
        SimpleDateFormat userFormatter = ModToolUserChatlogComposer.format;
        SimpleDateFormat issueFormatter = ModToolIssueChatlogComposer.format;
        try {
            ModToolUserChatlogComposer.format = new SimpleDateFormat("ss");
            ModToolIssueChatlogComposer.format = new SimpleDateFormat("ss");
            String expected = format("HH:mm", TIMESTAMP);

            assertEquals(expected, ModToolUserChatlogComposer.formatTimestamp(TIMESTAMP));
            assertEquals(expected, ModToolIssueChatlogComposer.formatTimestamp(TIMESTAMP));
        } finally {
            ModToolUserChatlogComposer.format = userFormatter;
            ModToolIssueChatlogComposer.format = issueFormatter;
        }
    }

    @Test
    void userInfoUsesAnImmutableFormatter() throws Exception {
        assertEquals(
                DateTimeFormatter.class,
                ModToolUserInfoComposer.class.getDeclaredField("DATE_FORMAT").getType());
    }

    private static String format(String pattern, int timestamp) {
        return new SimpleDateFormat(pattern).format(new Date(timestamp * 1000L));
    }
}
