package com.eu.habbo.habbohotel.gameclients;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.CryptoConfig;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Once a client logs in, its SSO ticket is gone from the database and /auth/sso-token finds it among the
 * online clients instead - and issued a new access token every time it was asked: a leaked ticket was
 * worth unlimited tokens for as long as the session lasted.
 */
class SsoTicketExchangeOnceTest {

    @Test
    void anOnlineTicketIsExchangedOnceAndANewTicketReopensIt() {
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            CryptoConfig crypto = mock(CryptoConfig.class);
            when(crypto.isEnabled()).thenReturn(false);
            emulator.when(Emulator::getCrypto).thenReturn(crypto);
            GameClient client = new GameClient(new EmbeddedChannel());
            client.setSsoTicket("ticket-1");

            assertTrue(client.claimSsoTicketExchange());
            assertFalse(client.claimSsoTicketExchange(), "the same ticket is not exchanged twice");

            client.setSsoTicket("ticket-2");
            assertTrue(client.claimSsoTicketExchange(), "a new ticket may be exchanged once");
        }
    }

    @Test
    void theTokenEndpointClaimsTheExchange() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java"));

        assertTrue(source.contains("online.getHabbo() != null && online.claimSsoTicketExchange()"));
    }
}
