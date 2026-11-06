package polaris.hotel.items.interactions.games;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;

/** A tick chain left over from before a pick-up stops instead of running beside the new one. */
class InteractionGameTimerChainTest {

    @Test
    void onlyTheNewestTickChainKeepsRunning() {
        Item item = mock(Item.class);
        when(item.getCustomParams()).thenReturn("30,60");
        InteractionGameTimer timer = new InteractionGameTimer(1, 1, item, "0", 0, 0);

        assertTrue(timer.tryActivateTimerThread());
        int oldChain = timer.getTimerChain();

        // Picked up while a tick was queued, placed again and started again.
        timer.setThreadActive(false);
        assertTrue(timer.tryActivateTimerThread());
        int newChain = timer.getTimerChain();

        assertFalse(timer.releaseTimerThread(oldChain));
        assertFalse(timer.tryActivateTimerThread());
        assertTrue(timer.releaseTimerThread(newChain));
        assertTrue(timer.tryActivateTimerThread());
    }
}
