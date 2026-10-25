package com.eu.habbo.messages.incoming.guides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.guides.GuideTour;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guide chat messages had no length limit and no word filter, and a report needed no one to report. */
class GuideUserMessageEventTest {

    @Test
    void aMessageIsTrimmedAndCut() {
        assertEquals(
                GuideTour.MAX_MESSAGE_LENGTH,
                GuideUserMessageEvent.normalize("x".repeat(400)).length());
        assertEquals("hi", GuideUserMessageEvent.normalize("  hi  "));
        assertEquals("", GuideUserMessageEvent.normalize(null));
    }

    @Test
    void theHandlersUseTheOpenTourFilterAndReportOnce() throws Exception {
        String message = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/guides/GuideUserMessageEvent.java"));
        String report = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/guides/GuideReportHelperEvent.java"));
        String typing = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/guides/GuideUserTypingEvent.java"));

        assertTrue(message.contains("getWordFilter().filter(text, habbo)"));
        assertTrue(message.contains("getActiveGuideTourByHabbo"));
        assertTrue(typing.contains("getActiveGuideTourByHabbo"));
        assertTrue(report.contains("if (reported == null) return;"));
        assertTrue(report.indexOf("endReportedSession(tour)") < report.indexOf("addTicket(issue)"));
    }
}
