package polaris.hotel.items.interactions.wired.effects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredServices;
import polaris.hotel.wired.core.WiredState;

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
