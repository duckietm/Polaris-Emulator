package com.eu.habbo.messages.incoming.soundboard;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SoundboardSaveVolumeEventTest {

    @Test
    void volumeIsClampedToTheSupportedRange() {
        assertEquals(0, SoundboardSaveVolumeEvent.clampVolume(-1));
        assertEquals(55, SoundboardSaveVolumeEvent.clampVolume(55));
        assertEquals(100, SoundboardSaveVolumeEvent.clampVolume(101));
    }
}
