package polaris.hotel.achievements.resolution;

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
        assertTrue(
                source("polaris/messages/incoming/Incoming.java").contains("ResetResolutionAchievementEvent = 3144"));

        String registry = source("polaris/messages/PacketManager.java");

        assertTrue(registry.contains("Incoming.RequestResolutionEvent, RequestResolutionEvent.class"));
        assertTrue(
                registry.contains("Incoming.ResetResolutionAchievementEvent, ResetResolutionAchievementEvent.class"));
    }

    @Test
    void thePickerNoLongerAnswersWithAPlaceholder() throws Exception {
        String composer = source("polaris/messages/outgoing/events/resolution/NewYearResolutionComposer.java");

        assertTrue(composer.contains("for (AchievementResolutionCandidate candidate : this.candidates)"));
        assertTrue(!composer.contains("NY2013RES"));
        assertTrue(composer.contains("@Deprecated"));
    }

    @Test
    void onlyTheOwnerOfTheFurniIsAnswered() throws Exception {
        for (String handler : new String[] {"RequestResolutionEvent", "ResetResolutionAchievementEvent"}) {
            assertTrue(
                    source("polaris/messages/incoming/unknown/" + handler + ".java")
                            .contains("item.getUserId() != user.getUserInfo().getId()"),
                    handler);
        }
    }

    @Test
    void aRunOutPromiseIsDroppedAndAKeptOneIsWrittenDown() throws Exception {
        String handler = source("polaris/messages/incoming/unknown/RequestResolutionEvent.java");

        assertTrue(handler.contains("resolution.expired(now)"));
        assertTrue(handler.contains("manager.clear(itemId)"));
        assertTrue(handler.contains("manager.kept(user, resolution)"));
        assertTrue(handler.contains("manager.complete(itemId, now)"));
    }

    @Test
    void aKeptPromiseIsNotResetAway() throws Exception {
        assertTrue(source("polaris/messages/incoming/unknown/ResetResolutionAchievementEvent.java")
                .contains("resolution == null || resolution.completed()"));
    }
}
