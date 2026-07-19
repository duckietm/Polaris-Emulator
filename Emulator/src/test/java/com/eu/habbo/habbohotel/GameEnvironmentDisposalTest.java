package com.eu.habbo.habbohotel;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameEnvironmentDisposalTest {

    @Test
    void partialEnvironmentCanBeDisposedSafely() {
        assertDoesNotThrow(() -> new GameEnvironment().dispose());
    }

    @Test
    void failedAndMissingStepsDoNotPreventLaterDisposal() {
        AtomicBoolean laterStepRan = new AtomicBoolean();
        Map<String, Runnable> steps = new LinkedHashMap<>();
        steps.put("missing", null);
        steps.put("failing", () -> {
            throw new IllegalStateException("expected");
        });
        steps.put("later", () -> laterStepRan.set(true));

        GameEnvironment.disposeAll(steps);

        assertTrue(laterStepRan.get());
    }

    @Test
    void roomsAreQuiescedBeforeTheRoomSavePassAndBotsAreDisposed()
            throws Exception {
        GameEnvironment environment = new GameEnvironment();
        com.eu.habbo.habbohotel.rooms.RoomManager rooms =
                mock(com.eu.habbo.habbohotel.rooms.RoomManager.class);
        com.eu.habbo.habbohotel.bots.BotManager bots =
                mock(com.eu.habbo.habbohotel.bots.BotManager.class);
        setField(environment, "roomManager", rooms);
        setField(environment, "botManager", bots);

        environment.dispose();

        var order = inOrder(rooms);
        order.verify(rooms).quiesceRoomCycles();
        order.verify(rooms).dispose();
        verify(bots).dispose();
    }

    private static void setField(
            GameEnvironment environment,
            String name,
            Object value) throws Exception {
        Field field = GameEnvironment.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(environment, value);
    }
}
