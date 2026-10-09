package com.eu.habbo.habbohotel.soundboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SoundboardRoomModeTest {

    @Test
    void theTwoValuesARoomAlwaysHadKeepTheirMeaning() {
        assertEquals(SoundboardRoomMode.OFF, SoundboardRoomMode.fromWire(0));
        assertEquals(SoundboardRoomMode.EVERYONE, SoundboardRoomMode.fromWire(1));
        assertEquals(SoundboardRoomMode.RIGHTS, SoundboardRoomMode.fromWire(2));
    }

    @Test
    void aCodeNobodyDefinedIsReadAsOff() {
        assertEquals(SoundboardRoomMode.OFF, SoundboardRoomMode.fromWire(3));
        assertEquals(SoundboardRoomMode.OFF, SoundboardRoomMode.fromWire(-1));
        assertEquals(SoundboardRoomMode.OFF, SoundboardRoomMode.fromWire(127));
    }

    @Test
    void everyModeRoundTripsThroughItsWireCode() {
        for (SoundboardRoomMode mode : SoundboardRoomMode.values()) {
            assertEquals(mode, SoundboardRoomMode.fromWire(mode.wireCode()));
        }
    }

    @Test
    void onlyOffIsDisabled() {
        assertFalse(SoundboardRoomMode.OFF.enabled());
        assertTrue(SoundboardRoomMode.EVERYONE.enabled());
        assertTrue(SoundboardRoomMode.RIGHTS.enabled());
    }

    @Test
    void whoMayPlayInEachMode() {
        assertFalse(SoundboardRoomMode.OFF.allows(true));
        assertFalse(SoundboardRoomMode.OFF.allows(false));
        assertTrue(SoundboardRoomMode.EVERYONE.allows(false));
        assertTrue(SoundboardRoomMode.EVERYONE.allows(true));
        assertTrue(SoundboardRoomMode.RIGHTS.allows(true));
        assertFalse(SoundboardRoomMode.RIGHTS.allows(false));
    }
}
