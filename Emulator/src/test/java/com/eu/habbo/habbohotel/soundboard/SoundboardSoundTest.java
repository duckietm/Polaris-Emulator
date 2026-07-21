package com.eu.habbo.habbohotel.soundboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundboardSoundTest {

    @Test
    void minimumRankIsInclusive() {
        SoundboardSound sound = new SoundboardSound(7, "Staff bell", "/sounds/staff.mp3", 5);

        assertFalse(sound.isAvailableTo(4));
        assertTrue(sound.isAvailableTo(5));
        assertTrue(sound.isAvailableTo(7));
    }

    @Test
    void minimumRankNeverDropsBelowOne() {
        SoundboardSound sound = new SoundboardSound(7, "Public bell", "/sounds/public.mp3", -3);

        assertTrue(sound.isAvailableTo(1));
    }
}
