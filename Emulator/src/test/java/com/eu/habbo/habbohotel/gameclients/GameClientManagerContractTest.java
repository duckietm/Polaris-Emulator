package com.eu.habbo.habbohotel.gameclients;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.CryptoConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameClientManagerContractTest {

    @Test
    void exposesExplicitForcedDisposePath() {
        assertDoesNotThrow(() -> GameClient.class.getDeclaredMethod("dispose", boolean.class));
        assertDoesNotThrow(() -> GameClientManager.class.getDeclaredMethod("forceDisposeClient", GameClient.class));
    }

    @Test
    void disposeMethodsIgnoreNullClient() {
        GameClientManager manager = new GameClientManager();

        assertDoesNotThrow(() -> manager.disposeClient(null));
        assertDoesNotThrow(() -> manager.forceDisposeClient(null));
    }

    @Test
    void gameClientDisposeIsExplicitlyIdempotent() throws Exception {
        assertTrue(java.util.concurrent.atomic.AtomicBoolean.class.isAssignableFrom(
                GameClient.class.getDeclaredField("disposed").getType()
        ));
    }

    @Test
    void reportsWhetherAUserOwnsAnAuthenticatedSession() throws Exception {
        var crypto = Emulator.class.getDeclaredField("crypto");
        crypto.setAccessible(true);
        crypto.set(null, new CryptoConfig(false, "", "", ""));

        GameClientManager manager = new GameClientManager();
        GameClient client = new GameClient(new io.netty.channel.embedded.EmbeddedChannel());

        assertEquals(0, manager.getAuthenticatedSessionCount(7));
        manager.claimAuthenticatedSession(7, client);
        assertEquals(1, manager.getAuthenticatedSessionCount(7));
        manager.releaseAuthenticatedSession(7, client);
        assertEquals(0, manager.getAuthenticatedSessionCount(7));
    }
}
