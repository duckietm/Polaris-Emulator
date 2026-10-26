package com.eu.habbo.messages.incoming.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The daily photo count and the cooldown lived in the session cache, which every login empties, and every error gave
 * the cooldown back: a relog every 50 photos, or an invalid payload retried at once, filled the disk.
 */
class CameraQuotaTest {

    @BeforeEach
    void reset() {
        CameraQuota.reset();
    }

    @Test
    void theDailyCountBelongsToTheUserNotTheSession() {
        for (int i = 0; i < 3; i++) CameraQuota.recordPhoto(7, 1_000 + i);

        assertEquals(3, CameraQuota.dailyCount(7, 2_000), "a new session reads the same count");
        assertEquals(0, CameraQuota.dailyCount(8, 2_000));
        assertEquals(0, CameraQuota.dailyCount(7, 1_000 + CameraQuota.DAY_SECONDS), "a new day starts over");
    }

    @Test
    void theCooldownOutlivesTheSessionAndOnlyAServerRefusalGivesItBack() {
        CameraQuota.markAttempt(7, 1_000);
        assertEquals(1_000, CameraQuota.lastAttempt(7));

        CameraQuota.refundAttempt(7);
        assertNull(CameraQuota.lastAttempt(7));
    }

    @Test
    void anInvalidUploadKeepsTheCooldown() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/camera/CameraRoomPictureEvent.java"));

        assertFalse(source.contains("cache.remove(COOLDOWN_KEY)"));
        assertFalse(source.contains("camera_daily_count"));
        int invalid = source.indexOf("attempted camera upload with invalid size");
        assertTrue(invalid > 0);
        assertFalse(source.substring(invalid, source.indexOf("this.image =", invalid))
                .contains("refundAttempt"));
    }
}
