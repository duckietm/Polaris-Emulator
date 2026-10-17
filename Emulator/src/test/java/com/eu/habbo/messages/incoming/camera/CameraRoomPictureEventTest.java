package com.eu.habbo.messages.incoming.camera;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraRoomPictureEventTest {
    @Test
    void onlyA320By320PhotoIsAccepted() {
        assertTrue(CameraRoomPictureEvent.hasPhotoDimensions(new int[] {320, 320}));

        assertFalse(CameraRoomPictureEvent.hasPhotoDimensions(new int[] {1024, 1024}));
        assertFalse(CameraRoomPictureEvent.hasPhotoDimensions(new int[] {320, 319}));
        assertFalse(CameraRoomPictureEvent.hasPhotoDimensions(new int[] {160, 160}));
        assertFalse(CameraRoomPictureEvent.hasPhotoDimensions(new int[] {0, 0}));
        assertFalse(CameraRoomPictureEvent.hasPhotoDimensions(null));
    }
}
