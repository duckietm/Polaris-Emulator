package com.eu.habbo.networking.gameserver.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NitroSecureAssetHandlerContractTest {
    private static String handlerSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/networking/gameserver/auth/NitroSecureAssetHandler.java"));
    }

    private static String emulatorSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/Emulator.java"));
    }

    @Test
    void secureAssetFilesAreSizeCheckedBeforeReadAndCache() throws Exception {
        String handler = handlerSource();
        String emulator = emulatorSource();

        int size = handler.indexOf("long size = Files.size(target)");
        int maxBytes = handler.indexOf("int maxBytes = maxAssetBytes(kind)", size);
        int oversizedGuard = handler.indexOf("size > maxBytes", maxBytes);
        int cacheLookup = handler.indexOf("CACHE.get(cacheKey)", oversizedGuard);
        int readAllBytes = handler.indexOf("Files.readAllBytes(target)", oversizedGuard);

        assertTrue(handler.contains("DEFAULT_MAX_CONFIG_BYTES = 2 * 1024 * 1024"),
                "Secure config assets should have a conservative default file cap");
        assertTrue(handler.contains("DEFAULT_MAX_GAMEDATA_BYTES = 16 * 1024 * 1024"),
                "Secure gamedata assets should have a bounded default file cap");
        assertTrue(handler.contains("nitro.secure.config.max_file_bytes"),
                "Secure config max file size should be configurable");
        assertTrue(handler.contains("nitro.secure.gamedata.max_file_bytes"),
                "Secure gamedata max file size should be configurable");
        assertTrue(size > -1, "Secure assets must inspect file size before loading bytes");
        assertTrue(maxBytes > size, "Secure assets must resolve the configured cap before loading bytes");
        assertTrue(oversizedGuard > maxBytes, "Secure assets must reject oversized files");
        assertTrue(oversizedGuard < cacheLookup, "Oversized secure assets must not be served from cache");
        assertTrue(oversizedGuard < readAllBytes, "Oversized secure assets must be rejected before readAllBytes");
        assertTrue(emulator.contains("register(\"nitro.secure.config.max_file_bytes\", \"2097152\")"),
                "Secure config max file size default must be registered before startup");
        assertTrue(emulator.contains("register(\"nitro.secure.gamedata.max_file_bytes\", \"16777216\")"),
                "Secure gamedata max file size default must be registered before startup");
    }
}
