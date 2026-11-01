package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The asset payload is lowercase hex, as the client decodes it, now written without a String.format per byte. */
class NitroSecureAssetHexTest {

    @Test
    void theHexIsLowercaseAndReadsBack() {
        byte[] bytes = {0x00, 0x0f, (byte) 0xab, (byte) 0xff};

        assertEquals("000fabff", NitroSecureAssetHandler.toHex(bytes));
        assertArrayEquals(bytes, NitroSecureAssetHandler.fromHex(NitroSecureAssetHandler.toHex(bytes)));
        assertEquals("", NitroSecureAssetHandler.toHex(new byte[0]));
    }
}
