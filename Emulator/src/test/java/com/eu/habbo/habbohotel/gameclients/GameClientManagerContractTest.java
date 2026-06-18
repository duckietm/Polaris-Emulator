package com.eu.habbo.habbohotel.gameclients;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
}
