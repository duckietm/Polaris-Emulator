package com.eu.habbo.habbohotel.bots;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisitorBotDateFormattingTest {

    @Test
    void preservesConfiguredTimestampPattern() {
        int timestamp = 1_700_000_000;
        String pattern = "yyyy-mm-dd HH:mm";
        VisitorBot.initialise(pattern);

        assertEquals(
                new SimpleDateFormat(pattern).format(new Date(timestamp * 1000L)),
                VisitorBot.formatTimestamp(timestamp));
    }

    @Test
    void visitorBotUsesAnImmutableFormatter() throws Exception {
        assertEquals(
                DateTimeFormatter.class,
                VisitorBot.class.getDeclaredField("DATE_FORMAT").getType());
    }
}
