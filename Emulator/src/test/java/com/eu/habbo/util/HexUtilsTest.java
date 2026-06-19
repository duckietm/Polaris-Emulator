package com.eu.habbo.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HexUtilsTest {

    @Test
    void toHexKnownValues() {
        assertEquals("00", HexUtils.toHex(new byte[]{0x00}));
        assertEquals("FF", HexUtils.toHex(new byte[]{(byte) 0xFF}));
        assertEquals("DEADBEEF", HexUtils.toHex(new byte[]{(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF}));
    }

    @Test
    void toHexEmpty() {
        assertEquals("", HexUtils.toHex(new byte[]{}));
    }

    @Test
    void toBytesKnownValues() {
        assertArrayEquals(new byte[]{(byte) 0xDE, (byte) 0xAD}, HexUtils.toBytes("DEAD"));
    }

    @Test
    void roundTrip() {
        byte[] original = {0x00, 0x7F, (byte) 0x80, (byte) 0xFF, 0x42, 0x13};
        assertArrayEquals(original, HexUtils.toBytes(HexUtils.toHex(original)));
    }

    @Test
    void getRandomHasRequestedLength() {
        assertEquals(16, HexUtils.getRandom(16).length());
        assertEquals(1, HexUtils.getRandom(1).length());
    }
}
