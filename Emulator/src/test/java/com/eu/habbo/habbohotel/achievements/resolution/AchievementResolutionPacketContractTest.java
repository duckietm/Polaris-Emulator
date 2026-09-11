package com.eu.habbo.habbohotel.achievements.resolution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AchievementResolutionPacketContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theResetHeaderMatchesTheRendererAndBothHandlersAreRegistered() throws Exception {
        assertTrue(source("com/eu/habbo/messages/incoming/Incoming.java")
                .contains("ResetResolutionAchievementEvent = 3144"));

        String registry = source("com/eu/habbo/messages/PacketManager.java");

        assertTrue(registry.contains("Incoming.RequestResolutionEvent, RequestResolutionEvent.class"));
        assertTrue(
                registry.contains("Incoming.ResetResolutionAchievementEvent, ResetResolutionAchievementEvent.class"));
    }

    @Test
    void thePickerNoLongerAnswersWithAPlaceholder() throws Exception {
        String composer = source("com/eu/habbo/messages/outgoing/events/resolution/NewYearResolutionComposer.java");

        assertTrue(composer.contains("for (AchievementResolutionCandidate candidate : this.candidates)"));
        assertTrue(!composer.contains("NY2013RES"));
        assertTrue(composer.contains("@Deprecated"));
    }

    @Test
    void onlyTheOwnerOfTheFurniIsAnswered() throws Exception {
        for (String handler : new String[] {"RequestResolutionEvent", "ResetResolutionAchievementEvent"}) {
            assertTrue(
                    source("com/eu/habbo/messages/incoming/unknown/" + handler + ".java")
                            .contains("item.getUserId() != habbo.getHabboInfo().getId()"),
                    handler);
        }
    }

    @Test
    void aRunOutPromiseIsDroppedAndAKeptOneIsWrittenDown() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/unknown/RequestResolutionEvent.java");

        assertTrue(handler.contains("resolution.expired(now)"));
        assertTrue(handler.contains("manager.clear(itemId)"));
        assertTrue(handler.contains("manager.kept(habbo, resolution)"));
        assertTrue(handler.contains("manager.complete(itemId, now)"));
    }

    @Test
    void aKeptPromiseIsNotResetAway() throws Exception {
        assertTrue(source("com/eu/habbo/messages/incoming/unknown/ResetResolutionAchievementEvent.java")
                .contains("resolution == null || resolution.completed()"));
    }
}
