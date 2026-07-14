package com.eu.habbo.networking.gameserver.e2e;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class E2eMessengerFixtureContractTest {
    private static final Path REPOSITORY = Path.of("..").toAbsolutePath().normalize();

    @Test
    void seedsTwoTicketedUsersAndMutualFriendship() throws IOException {
        String seed = Files.readString(REPOSITORY.resolve("e2e/seed.sql"));
        assertTrue(seed.contains("@e2e_second_sso_ticket"));
        assertTrue(seed.contains("900003, 'e2e_messenger_peer'"));
        assertTrue(seed.contains("(900001, 900003, UNIX_TIMESTAMP())"));
        assertTrue(seed.contains("(900003, 900001, UNIX_TIMESTAMP())"));
    }

    @Test
    void bothSetupScriptsRequireTheSecondTicket() throws IOException {
        String shell = Files.readString(REPOSITORY.resolve("e2e/prepare-database.sh"));
        String powershell = Files.readString(REPOSITORY.resolve("e2e/prepare-database.ps1"));
        assertTrue(shell.contains("E2E_SECOND_SSO_TICKET"));
        assertTrue(powershell.contains("E2E_SECOND_SSO_TICKET"));
    }
}
