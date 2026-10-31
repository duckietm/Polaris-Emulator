package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** After a rename the cached availability of both names stayed until it expired. */
class RenameAvailabilityCacheContractTest {

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/" + path));
    }

    @Test
    void theWebRenameForgetsBothNames() throws Exception {
        String endpoints = source("networking/gameserver/auth/AccountChangeEndpoints.java");

        assertTrue(endpoints.contains("refreshRenameCaches(userId, account.username(), newUsername);"));
        assertTrue(endpoints.contains("AvailabilityCache.invalidateUsername(oldUsername);"));
        assertTrue(endpoints.contains("AvailabilityCache.invalidateUsername(newUsername);"));
    }

    @Test
    void theInGameRenameForgetsBothNames() throws Exception {
        String rename = source("messages/incoming/users/ConfirmChangeNameEvent.java");

        assertTrue(rename.contains("AvailabilityCache.invalidateUsername(oldName);"));
        assertTrue(rename.contains("AvailabilityCache.invalidateUsername(name);"));
    }
}
