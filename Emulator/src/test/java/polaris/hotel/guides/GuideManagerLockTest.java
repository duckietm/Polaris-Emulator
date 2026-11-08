package polaris.hotel.guides;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;

/** Going on duty while findHelper walks the helpers waits for the walk, instead of changing the map under it. */
class GuideManagerLockTest {

    @Test
    void goingOnDutyTakesTheLockTheHelperSearchHolds() throws Exception {
        GuideManager manager = new GuideManager();
        Field field = GuideManager.class.getDeclaredField("activeHelpers");
        field.setAccessible(true);
        Object helpers = field.get(manager);

        CountDownLatch done = new CountDownLatch(1);
        Thread onDuty;
        synchronized (helpers) {
            onDuty = new Thread(() -> {
                manager.setOnGuide(mock(User.class), true);
                done.countDown();
            });
            onDuty.start();
            assertFalse(done.await(200, TimeUnit.MILLISECONDS), "the put must wait for the iterating lock");
        }
        assertTrue(done.await(5, TimeUnit.SECONDS));
        onDuty.join();
    }
}
