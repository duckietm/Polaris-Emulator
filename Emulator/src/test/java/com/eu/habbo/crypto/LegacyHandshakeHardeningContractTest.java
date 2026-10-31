package com.eu.habbo.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The legacy handshake (enc.enabled=1) used a predictable random, could be repeated and decrypted any number of blocks. */
class LegacyHandshakeHardeningContractTest {

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/" + path));
    }

    @Test
    void keysAndPaddingUseASecureRandom() throws Exception {
        String diffie = source("crypto/HabboDiffieHellman.java");
        String rsa = source("crypto/HabboRSACrypto.java");

        assertFalse(diffie.contains("ThreadLocalRandom"));
        assertFalse(rsa.contains("ThreadLocalRandom"));
        assertTrue(diffie.contains("new SecureRandom()"));
        assertTrue(rsa.contains("new SecureRandom()"));
    }

    @Test
    void aClientNumberIsOneRsaBlock() throws Exception {
        assertTrue(source("crypto/HabboDiffieHellman.java").contains("if (bytes.length != this.crypto.blockSize()) {"));
    }

    @Test
    void theHandshakeCompletesOnce() throws Exception {
        String complete = source("messages/incoming/handshake/CompleteDiffieHandshakeEvent.java");

        int guard = complete.indexOf("if (this.client.isHandshakeFinished()) {");
        assertTrue(guard > 0 && guard < complete.indexOf("getSharedKey("));
    }
}
