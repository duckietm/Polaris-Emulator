package polaris.threading;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.jupiter.api.Test;

class PolarisExecutorServiceRejectionTest {

    @Test
    void rejectedTaskRemainsExplicitToTheCaller() {
        PolarisExecutorService executor = new PolarisExecutorService(1, Executors.defaultThreadFactory());
        assertInstanceOf(RejectedExecutionHandlerImpl.class, executor.getRejectedExecutionHandler());
        executor.shutdownNow();

        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {}));
    }

    @Test
    void publicStandaloneHandlerPreservesLoggingOnlyBehavior() {
        RejectedExecutionHandlerImpl handler = new RejectedExecutionHandlerImpl();
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.shutdownNow();

        assertDoesNotThrow(() -> handler.rejectedExecution(() -> {}, executor));
    }
}
