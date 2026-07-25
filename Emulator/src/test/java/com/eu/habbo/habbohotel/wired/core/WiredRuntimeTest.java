package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class WiredRuntimeTest {

    @Test
    void ownsStartupAndOrderedShutdown() {
        WiredEngine engine = mock(WiredEngine.class);
        RoomWiredStackIndex stackIndex = mock(RoomWiredStackIndex.class);
        WiredTickService tickService = mock(WiredTickService.class);
        WiredRuntime runtime = new WiredRuntime(engine, stackIndex, tickService);

        runtime.start();
        assertTrue(runtime.isActive());

        runtime.shutdown();
        assertFalse(runtime.isActive());

        InOrder shutdownOrder = inOrder(tickService, stackIndex, engine);
        shutdownOrder.verify(tickService).start();
        shutdownOrder.verify(tickService).stop();
        shutdownOrder.verify(engine).shutdownScheduledWork();
        shutdownOrder.verify(stackIndex).clearAll();
        shutdownOrder.verify(engine).clearUnseenCache();
        shutdownOrder.verify(engine).clearAllDiagnostics();
        shutdownOrder.verify(engine).clearAllExecutionCaches();
    }

    @Test
    void lifecycleOperationsAreIdempotent() {
        WiredEngine engine = mock(WiredEngine.class);
        RoomWiredStackIndex stackIndex = mock(RoomWiredStackIndex.class);
        WiredTickService tickService = mock(WiredTickService.class);
        WiredRuntime runtime = new WiredRuntime(engine, stackIndex, tickService);

        runtime.start();
        runtime.start();
        runtime.shutdown();
        runtime.shutdown();

        verify(tickService, times(1)).start();
        verify(tickService, times(1)).stop();
        verify(stackIndex, times(1)).clearAll();
    }

    @Test
    void concurrentLifecycleCallsAreSerializedAndRuntimeCanRestart() throws Exception {
        int workers = 12;
        WiredEngine engine = mock(WiredEngine.class);
        RoomWiredStackIndex stackIndex = mock(RoomWiredStackIndex.class);
        WiredTickService tickService = mock(WiredTickService.class);
        WiredRuntime runtime = new WiredRuntime(engine, stackIndex, tickService);
        ExecutorService executor = Executors.newFixedThreadPool(workers);

        try {
            runConcurrently(executor, workers, runtime::start);
            assertTrue(runtime.isActive());
            verify(tickService, times(1)).start();

            runConcurrently(executor, workers, runtime::shutdown);
            assertFalse(runtime.isActive());
            verify(tickService, times(1)).stop();
            verify(stackIndex, times(1)).clearAll();

            runtime.start();
            runtime.shutdown();
            verify(tickService, times(2)).start();
            verify(tickService, times(2)).stop();
            verify(stackIndex, times(2)).clearAll();
        } finally {
            executor.shutdownNow();
        }
    }

    private static void runConcurrently(ExecutorService executor, int workers, Runnable operation) throws Exception {
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int worker = 0; worker < workers; worker++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(2, TimeUnit.SECONDS));
                operation.run();
                return null;
            }));
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        for (Future<?> future : futures) {
            future.get(2, TimeUnit.SECONDS);
        }
    }
}
