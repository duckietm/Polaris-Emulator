package com.eu.habbo.messages.incoming.camera;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PhotoCompetitionEventTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theHeaderMatchesTheRendererAndIsRegistered() throws Exception {
        assertTrue(source("com/eu/habbo/messages/incoming/Incoming.java").contains("PhotoCompetitionEvent = 3959"));
        assertTrue(source("com/eu/habbo/messages/PacketManager.java")
                .contains("Incoming.PhotoCompetitionEvent, PhotoCompetitionEvent.class"));
    }

    @Test
    void aCheckoutWithoutAPhotoIsRefusedLikePublishingRefusesIt() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/camera/PhotoCompetitionEvent.java");

        assertTrue(handler.contains("timestamp == 0 || photo == null || photo.isEmpty()"));
        assertTrue(handler.contains("!photo.contains(Integer.toString(timestamp))"));
        assertTrue(handler.contains("new CameraCompetitionStatusComposer(false, FAILED)"));
    }

    @Test
    void theDailyAllowanceIsCheckedBeforeAnythingIsStored() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/camera/PhotoCompetitionEvent.java");

        assertTrue(handler.contains("competition.submissionsToday(info.getId()) >= competition.dailyLimit()"));
        assertTrue(handler.contains("new CameraCompetitionStatusComposer(false, TOO_MANY)"));
        assertTrue(handler.indexOf("submissionsToday") < handler.indexOf("competition.submit("));
    }

    @Test
    void aSubmissionThatCouldNotBeStoredPromisesNothing() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/camera/PhotoCompetitionEvent.java");

        assertTrue(handler.contains("new CameraCompetitionStatusComposer(stored, stored ? \"\" : FAILED)"));
    }

    @Test
    void aDatabaseErrorCountsAsNoAllowanceLeftInsteadOfAFreePass() throws Exception {
        String manager = source("com/eu/habbo/habbohotel/camera/CameraCompetitionManager.java");

        assertTrue(manager.contains("return Integer.MAX_VALUE;"));
        assertTrue(manager.contains("Math.max(1, Emulator.getConfig().getInt(\"camera.competition.daily.limit\", 3))"));
    }
}
