package com.eu.habbo.habbohotel.modtool;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ModToolBanDateFormattingTest {

    @Test
    void preservesBanTimestampTextAndPublicFieldMutability() {
        int timestamp = 1_700_000_000;
        String expected = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new Date(timestamp * 1000L));

        assertEquals(expected, ModToolBan.formatTimestamp(timestamp));

        SimpleDateFormat original = ModToolBan.dateFormat;
        try {
            SimpleDateFormat replacement = new SimpleDateFormat("ss");
            ModToolBan.dateFormat = replacement;
            assertSame(replacement, ModToolBan.dateFormat);
        } finally {
            ModToolBan.dateFormat = original;
        }
    }
}
