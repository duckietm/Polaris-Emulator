package com.eu.habbo.threading;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HabboExecutorServicePeriodicTaskTest {
    @Test
    void periodicTaskContinuesAfterOneExecutionThrows() throws Exception {
        HabboExecutorService executor = new HabboExecutorService(1, Executors.defaultThreadFactory());
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch recoveredExecution = new CountDownLatch(1);

        try {
            executor.scheduleAtFixedRate(() -> {
                if (executions.incrementAndGet() == 1) {
                    throw new IllegalStateException("first execution fails");
                }
                recoveredExecution.countDown();
            }, 0, 10, TimeUnit.MILLISECONDS);

            assertTrue(recoveredExecution.await(2, TimeUnit.SECONDS),
                    "an exception must not permanently cancel a periodic emulator task");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void ioExceptionFromPeriodicTaskIsObservable() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(
                HabboExecutorService.class);
        CountDownLatch logged = new CountDownLatch(1);
        AppenderBase<ILoggingEvent> appender =
                new AppenderBase<>() {
                    @Override
                    protected void append(ILoggingEvent event) {
                        if (event.getLevel() == Level.ERROR
                                && event.getThrowableProxy() != null
                                && IOException.class.getName().equals(
                                        event.getThrowableProxy()
                                                .getClassName())) {
                            logged.countDown();
                        }
                    }
                };
        appender.start();
        logger.addAppender(appender);

        HabboExecutorService executor = new HabboExecutorService(
                1, Executors.defaultThreadFactory());
        try {
            executor.scheduleAtFixedRate(
                    () -> {
                        sneakyThrow(new IOException(
                                "simulated task failure"));
                    },
                    0,
                    10,
                    TimeUnit.MILLISECONDS);

            assertTrue(logged.await(1, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void executorInstallsItsRejectionPolicy() {
        HabboExecutorService executor = new HabboExecutorService(
                1, Executors.defaultThreadFactory());
        try {
            assertInstanceOf(
                    RejectedExecutionHandlerImpl.class,
                    executor.getRejectedExecutionHandler());
        } finally {
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(
            Throwable failure) throws E {
        throw (E) failure;
    }
}
