package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressAchievementGuardTest {
    @Test
    void parsesInvalidProgressCeilingsAsDefault() {
        assertEquals(ProgressAchievement.DEFAULT_MAX_PROGRESS, ProgressAchievement.parseMaxProgress(null));
        assertEquals(ProgressAchievement.DEFAULT_MAX_PROGRESS, ProgressAchievement.parseMaxProgress("0"));
        assertEquals(50, ProgressAchievement.parseMaxProgress("50"));
    }

    @Test
    void validatesAchievementProgressPayload() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/ProgressAchievement.java"));

        assertTrue(source.contains("@Positive(message = \"invalid user\")"),
                "RCON achievement progress must reject invalid target users before execution");
        assertTrue(source.contains("@Positive(message = \"invalid achievement\")"),
                "RCON achievement progress must reject invalid achievement ids before execution");
        assertTrue(source.contains("@Positive(message = \"invalid progress\")"),
                "RCON achievement progress must reject zero or negative progress before execution");
        assertTrue(source.contains("json.progress > maxProgress"),
                "RCON achievement progress must reject configured-progress overflows");
        assertTrue(source.contains("rcon.achievement.max_progress"),
                "RCON achievement progress ceiling must be configurable");
    }
}
