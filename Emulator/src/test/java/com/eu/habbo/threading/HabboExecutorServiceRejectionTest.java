package com.eu.habbo.threading;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HabboExecutorServiceRejectionTest {

    @Test
    void rejectedTaskRemainsExplicitToTheCaller() {
        HabboExecutorService executor = new HabboExecutorService(
                1,
                Executors.defaultThreadFactory());
        assertInstanceOf(
                RejectedExecutionHandlerImpl.class,
                executor.getRejectedExecutionHandler(),
                "the project rejection handler must be installed");
        executor.shutdownNow();

        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
        }));
    }
}
