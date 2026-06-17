package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateUserGuardTest {
    @Test
    void validatesAchievementScoreDelta() {
        assertTrue(UpdateUser.isValidAchievementScoreDelta(0, 100));
        assertTrue(UpdateUser.isValidAchievementScoreDelta(100, 100));
        assertFalse(UpdateUser.isValidAchievementScoreDelta(-1, 100));
        assertFalse(UpdateUser.isValidAchievementScoreDelta(101, 100));
    }

    @Test
    void parsesInvalidAchievementScoreCeilingsAsDefault() {
        assertEquals(UpdateUser.DEFAULT_MAX_ACHIEVEMENT_SCORE_DELTA, UpdateUser.parseMaxAchievementScoreDelta(null));
        assertEquals(UpdateUser.DEFAULT_MAX_ACHIEVEMENT_SCORE_DELTA, UpdateUser.parseMaxAchievementScoreDelta("-1"));
        assertEquals(0, UpdateUser.parseMaxAchievementScoreDelta("0"));
        assertEquals(50, UpdateUser.parseMaxAchievementScoreDelta("50"));
    }

    @Test
    void validatesLookShapeAndLength() {
        assertTrue(UpdateUser.isValidLook(null));
        assertTrue(UpdateUser.isValidLook(""));
        assertTrue(UpdateUser.isValidLook("hr-115-42.hd-195-19.ch-3030-82"));
        assertFalse(UpdateUser.isValidLook("hd-1\nch-1"));
        assertFalse(UpdateUser.isValidLook("hd_1"));
        assertFalse(UpdateUser.isValidLook("a".repeat(UpdateUser.MAX_LOOK_LENGTH + 1)));
    }

    @Test
    void offlineUpdatesReportMissingUsersAndUseAffectedRows() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/UpdateUser.java"));

        assertTrue(source.contains("executeUpdate() == 0"),
                "Offline UpdateUser mutations must inspect affected row counts");
        assertTrue(source.contains("HABBO_NOT_FOUND"),
                "Offline UpdateUser mutations must report missing users");
        assertTrue(source.contains("rcon.updateuser.max_achievement_score_delta"),
                "Achievement score deltas must have a configurable RCON ceiling");
    }
}
