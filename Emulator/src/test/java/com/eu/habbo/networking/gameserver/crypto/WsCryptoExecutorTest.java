package com.eu.habbo.networking.gameserver.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;

class WsCryptoExecutorTest {

    @Test
    void executorIsFixedWidthAndBounded() {
        ThreadPoolExecutor executor = WsCryptoExecutor.createExecutor(3);
        try {
            assertEquals(3, executor.getCorePoolSize());
            assertEquals(3, executor.getMaximumPoolSize());
            assertEquals(256, executor.getQueue().remainingCapacity());
            assertTrue(executor.allowsCoreThreadTimeOut());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void workersAreDaemonThreadsWithDedicatedNames() throws Exception {
        ThreadPoolExecutor executor = WsCryptoExecutor.createExecutor(1);
        try {
            String worker = executor.submit(() -> Thread.currentThread().getName()
                            + ":"
                            + Thread.currentThread().isDaemon())
                    .get();

            assertTrue(worker.startsWith("ws-crypto-worker-"));
            assertTrue(worker.endsWith(":true"));
        } finally {
            executor.shutdownNow();
        }
    }
}
