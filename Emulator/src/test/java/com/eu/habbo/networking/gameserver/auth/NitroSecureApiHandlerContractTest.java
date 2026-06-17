package com.eu.habbo.networking.gameserver.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NitroSecureApiHandlerContractTest {
    private static String handlerSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/networking/gameserver/auth/NitroSecureApiHandler.java"));
    }

    private static String emulatorSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/Emulator.java"));
    }

    @Test
    void encryptedApiPayloadSizeIsBoundedBeforeCopyAndDecrypt() throws Exception {
        String handler = handlerSource();
        String emulator = emulatorSource();

        int readableBytes = handler.indexOf("int readableBytes = req.content().readableBytes()");
        int maxPayload = handler.indexOf("int maxPayloadBytes = maxPayloadBytes()", readableBytes);
        int oversizedGuard = handler.indexOf("readableBytes > maxPayloadBytes", maxPayload);
        int byteArray = handler.indexOf("new byte[readableBytes]", readableBytes);
        int decrypt = handler.indexOf("NitroSecureAssetHandler.decrypt", byteArray);

        assertTrue(handler.contains("DEFAULT_MAX_PAYLOAD_BYTES = 64 * 1024"),
                "Secure API handler should have a conservative default payload cap");
        assertTrue(handler.contains("nitro.secure.api.max_payload_bytes"),
                "Secure API max payload should be configurable");
        assertTrue(readableBytes > -1, "Secure API handler must read content size before allocation");
        assertTrue(maxPayload > readableBytes, "Secure API handler must resolve max payload before allocation");
        assertTrue(oversizedGuard > maxPayload, "Secure API handler must reject oversized encrypted payloads");
        assertTrue(oversizedGuard < byteArray, "Oversized encrypted payloads must be rejected before byte array allocation");
        assertTrue(byteArray < decrypt, "Secure API payload must be bounded before decrypting");
        assertTrue(handler.contains("REQUEST_ENTITY_TOO_LARGE"),
                "Secure API callers need a deterministic status for oversized encrypted payloads");
        assertTrue(emulator.contains("register(\"nitro.secure.api.max_payload_bytes\", \"65536\")"),
                "Secure API max payload default must be registered before startup");
    }
}
