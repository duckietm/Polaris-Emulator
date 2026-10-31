package com.eu.habbo.messages.incoming.camera;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Publishing a photo charged with a void call after the insert, so a refused debit published it for free. */
class CameraPublishChargeContractTest {

    @Test
    void thePointsAreTakenBeforeTheInsertAndGivenBackWhenItFails() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/camera/CameraPublishToWebEvent.java"));

        assertFalse(source.contains("habbo.givePoints(CAMERA_PUBLISH_POINTS_TYPE, -CAMERA_PUBLISH_POINTS);"));

        int charge = source.indexOf("habbo.tryTakePoints(CAMERA_PUBLISH_POINTS_TYPE, CAMERA_PUBLISH_POINTS)");
        int insert = source.indexOf("INSERT INTO camera_web");
        assertTrue(charge > 0 && charge < insert);
        assertTrue(source.contains("\"camera.publish.refund\""));
    }
}
