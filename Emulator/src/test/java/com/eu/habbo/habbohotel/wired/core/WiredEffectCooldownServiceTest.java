package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class WiredEffectCooldownServiceTest {

    @Test
    void preservesTheLegacyCustomWiredPerUserBypassAtomically() {
        WiredEffectCooldownService service = new WiredEffectCooldownService(() -> true);
        InteractionWiredEffect effect = mock(InteractionWiredEffect.class, Answers.CALLS_REAL_METHODS);
        doReturn(true).when(effect).requiresTriggeringUser();
        Map<Integer, Long> userClaims = new HashMap<>();
        doAnswer(invocation -> {
                    userClaims.put(invocation.getArgument(0), invocation.getArgument(1));
                    return null;
                })
                .when(effect)
                .addUserExecutionCache(anyInt(), anyLong());
        doAnswer(invocation -> {
                    int userId = invocation.getArgument(0);
                    long timestamp = invocation.getArgument(1);
                    Long previous = userClaims.get(userId);
                    return previous == null || timestamp - previous >= 100L;
                })
                .when(effect)
                .userCanExecute(anyInt(), anyLong());
        WiredContext firstUser = context(1);
        WiredContext secondUser = context(2);

        assertTrue(service.tryAcquire(effect, firstUser, 1_000L));
        assertTrue(service.tryAcquire(effect, secondUser, 1_020L));
        assertFalse(service.tryAcquire(effect, secondUser, 1_030L));
        assertFalse(service.tryAcquire(effect, firstUser, 1_050L));
        assertTrue(service.tryAcquire(effect, firstUser, 1_070L));
    }

    @Test
    void honorsPluginInterfaceCooldownsWithoutRetainingPluginEffectsAfterClear() {
        WiredEffectCooldownService service = new WiredEffectCooldownService(() -> false);
        IWiredEffect pluginEffect = new IWiredEffect() {
            @Override
            public void execute(WiredContext context) {}

            @Override
            public long getCooldown() {
                return 100L;
            }
        };

        assertTrue(service.tryAcquire(pluginEffect, null, 2_000L));
        assertFalse(service.tryAcquire(pluginEffect, null, 2_099L));
        assertTrue(service.tryAcquire(pluginEffect, null, 2_100L));

        service.clear();
        assertTrue(service.tryAcquire(pluginEffect, null, 2_001L));
    }

    private static WiredContext context(int actorId) {
        Room room = mock(Room.class);
        RoomUnit actor = mock(RoomUnit.class);
        when(actor.getId()).thenReturn(actorId);
        return new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).actor(actor).build(),
                null,
                mock(WiredServices.class),
                new WiredState(10));
    }
}
