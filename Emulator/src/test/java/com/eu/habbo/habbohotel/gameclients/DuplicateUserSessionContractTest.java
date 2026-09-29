package com.eu.habbo.habbohotel.gameclients;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.CryptoConfig;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DuplicateUserSessionContractTest {
    @Test
    void latestClientAtomicallyOwnsTheUserSession() throws Exception {
        var crypto = Emulator.class.getDeclaredField("crypto");
        crypto.setAccessible(true);
        crypto.set(null, new CryptoConfig(false, "", "", ""));

        GameClientManager manager = new GameClientManager();
        GameClient first = new GameClient(new EmbeddedChannel());
        GameClient second = new GameClient(new EmbeddedChannel());

        assertNull(manager.claimAuthenticatedSession(7, first));
        assertSame(first, manager.claimAuthenticatedSession(7, second));
        manager.releaseAuthenticatedSession(7, first);
        assertSame(second, manager.getAuthenticatedClient(7));
        manager.releaseAuthenticatedSession(7, second);
        assertNull(manager.getAuthenticatedClient(7));
    }

    @Test
    void secureLoginClaimsByUserIdBeforeConnecting() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java"));
        int claim = source.indexOf("claimAuthenticatedSession(");
        int connect = source.indexOf(".connect()", claim);
        int forcedDispose = source.indexOf("disconnectWithReason(previousClient", claim);

        assertTrue(claim > -1, "SecureLoginEvent must atomically claim the user id");
        assertTrue(forcedDispose > claim, "the displaced client must be closed without parking a ghost session");
        assertTrue(connect > forcedDispose, "the previous session must be removed before the new login connects");
    }

    @Test
    void disconnectWithReasonTellsTheClientThenClosesWithoutParking() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/gameclients/GameClientManager.java"));
        int method = source.indexOf("public void disconnectWithReason(");
        int send = source.indexOf("new DisconnectReasonComposer(reason)", method);
        int flush = source.indexOf("channel.flush()", send);
        int dispose = source.indexOf("this.forceDisposeClient(client)", flush);

        assertTrue(method > -1);
        assertTrue(send > method, "the reason is sent first");
        assertTrue(flush > send, "and flushed before the close");
        assertTrue(dispose > flush, "then the session closes without a ghost session");
    }
}
