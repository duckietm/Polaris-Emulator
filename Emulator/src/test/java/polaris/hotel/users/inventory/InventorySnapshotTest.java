package polaris.hotel.users.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import polaris.hotel.bots.Bot;

/**
 * The inventory composer iterated the live bot map while another user's pickup added to it, and the empty-inventory
 * commands copied and cleared it in two steps.
 */
class InventorySnapshotTest {

    private static BotsComponent withBots(Bot... bots) throws Exception {
        BotsComponent component = mock(BotsComponent.class, CALLS_REAL_METHODS);
        Map<Integer, Bot> map = new HashMap<>();
        for (Bot bot : bots) map.put(bot.getId(), bot);
        Field field = BotsComponent.class.getDeclaredField("bots");
        field.setAccessible(true);
        field.set(component, map);
        return component;
    }

    private static Bot bot(int id) {
        Bot bot = mock(Bot.class);
        when(bot.getId()).thenReturn(id);
        return bot;
    }

    @Test
    void aSnapshotIsACopyThatLaterChangesDoNotTouch() throws Exception {
        BotsComponent component = withBots(bot(1));

        List<Bot> snapshot = component.getBotsSnapshot();
        component.addBot(bot(2));

        assertEquals(1, snapshot.size());
    }

    @Test
    void takingAllEmptiesTheInventoryAndReturnsWhatItHeld() throws Exception {
        BotsComponent component = withBots(bot(1), bot(2));

        assertEquals(2, component.takeAllBots().size());
        assertTrue(component.getBots().isEmpty());
    }

    @Test
    void aFullInventoryRefusesTheBotAndOneWithRoomTakesIt() throws Exception {
        BotsComponent component = withBots(bot(1));

        assertFalse(component.addBotWithin(bot(2), 1));
        assertTrue(component.addBotWithin(bot(2), 2));
        assertEquals(2, component.getBots().size());
    }

    @Test
    void concurrentPickupsNeverPassTheMaximum() throws Exception {
        BotsComponent component = withBots();
        int maximum = 5;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        for (int id = 1; id <= 50; id++) {
            Bot bot = bot(id);
            tasks.add(pool.submit(() -> {
                start.await();
                return component.addBotWithin(bot, maximum);
            }));
        }
        start.countDown();
        for (Future<?> task : tasks) task.get();
        pool.shutdown();

        assertEquals(maximum, component.getBots().size());
    }
}
