package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import org.junit.jupiter.api.Test;

class WiredEffectResetTimersTest {

    @Test
    void executionUsesTheContextServiceBoundary() {
        Room room = mock(Room.class);
        WiredServices services = mock(WiredServices.class);
        WiredContext context = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build(), null, services, new WiredState(100));
        WiredEffectResetTimers effect = new WiredEffectResetTimers(42, 7, mock(Item.class), "0", 0, 0);

        effect.execute(context);

        verify(services).resetTimers(room);
    }
}
