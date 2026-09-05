package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WiredExtraVariableWebApiTest {

    private static final Pattern URL_SAFE = Pattern.compile("[A-Za-z0-9_-]+");

    @Test
    void mintedKeysAreUrlSafeAndCarryTheWholeRandomDraw() {
        String key = WiredExtraVariableWebApi.mintKey();

        // 24 bytes is 32 base64 characters once padding is dropped. A key travels in a URL and in a
        // header, so anything needing escaping there would be reshaped in transit and stop matching.
        assertEquals(32, key.length());
        assertTrue(URL_SAFE.matcher(key).matches(), key);
    }

    @Test
    void everyMintedKeyIsDifferent() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(WiredExtraVariableWebApi.mintKey());
        }

        // A collision here would mean two rooms sharing one credential.
        assertEquals(500, seen.size());
    }

    @Test
    void theVariableIsReadFromTheFirstFieldSoTheKeysTheClientEchoesAreIgnored() {
        // The client is sent variable, read key and write key, and sends the whole line back. Only
        // the first field is a client decision; the keys belong to the server.
        assertEquals("42", WiredExtraVariableWebApi.firstField("42\tREADKEY\tWRITEKEY"));
        assertEquals("42", WiredExtraVariableWebApi.firstField("42"));
        assertEquals("", WiredExtraVariableWebApi.firstField(""));
        assertEquals("", WiredExtraVariableWebApi.firstField(null));
    }

    @Test
    void aKeyNobodyMintedOpensNothing() {
        // The HTTP surface answers "unknown key" on null, so an absent query parameter and a wrong
        // one take the same path and reveal nothing about which one it was.
        assertNull(WiredExtraVariableWebApi.resolve(null));
        assertNull(WiredExtraVariableWebApi.resolve(""));
        assertNull(WiredExtraVariableWebApi.resolve(WiredExtraVariableWebApi.mintKey()));
    }

    @Test
    void aVariableTokenThatIsNotAnItemIdResolvesToNothing() {
        assertEquals(42, WiredExtraVariableWebApi.getCustomItemId(" 42 "));
        assertEquals(0, WiredExtraVariableWebApi.getCustomItemId("not-an-id"));
        assertEquals(0, WiredExtraVariableWebApi.getCustomItemId(""));
        assertEquals(0, WiredExtraVariableWebApi.getCustomItemId(null));
        // saveData refuses anything that resolves to 0, so an unparsable token cannot bind.
        assertFalse(WiredExtraVariableWebApi.getCustomItemId("9999999999999") > 0);
    }
}
