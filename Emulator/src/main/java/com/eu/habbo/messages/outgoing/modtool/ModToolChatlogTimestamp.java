package com.eu.habbo.messages.outgoing.modtool;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * How a chat line is stamped in the moderation tool.
 *
 * <p>Every chatlog used to read {@code HH:mm} and nothing else, so a moderator reading a report
 * about yesterday saw "12:04" with no way to tell which day it belonged to. The day is added only
 * when the line is not from today, which keeps a live room's log as short as it was.
 *
 * <p>The field is a free-form string on the wire, so this changes what is written in it and not the
 * shape of any packet.
 */
public final class ModToolChatlogTimestamp {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZONE);
    private static final DateTimeFormatter DATE_AND_TIME =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZONE);

    private ModToolChatlogTimestamp() {}

    /** @param epochSeconds when the line was said */
    public static String format(long epochSeconds) {
        return format(Instant.ofEpochSecond(epochSeconds));
    }

    public static String format(Instant instant) {
        if (instant == null) return "";

        return LocalDate.ofInstant(instant, ZONE).equals(LocalDate.now(ZONE))
                ? TIME.format(instant)
                : DATE_AND_TIME.format(instant);
    }
}
