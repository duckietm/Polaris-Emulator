package com.eu.habbo.threading;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    void periodicIoFailureIsReportedWhenAFileAppenderFails() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(HabboExecutorService.class);
        Level previousLevel = logger.getLevel();
        boolean previousAdditivity = logger.isAdditive();
        ListAppender<ILoggingEvent> observedEvents = new ListAppender<>();
        AppenderBase<ILoggingEvent> failingFileAppender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent eventObject) {
                throw new IllegalStateException("runtime error file is unavailable");
            }
        };
        observedEvents.setContext(logger.getLoggerContext());
        failingFileAppender.setContext(logger.getLoggerContext());
        observedEvents.start();
        failingFileAppender.start();
        logger.setLevel(Level.ERROR);
        logger.setAdditive(false);
        logger.addAppender(failingFileAppender);
        logger.addAppender(observedEvents);

        HabboExecutorService executor = new HabboExecutorService(
                1,
                Executors.defaultThreadFactory());
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch recoveredExecution = new CountDownLatch(1);

        try {
            executor.scheduleAtFixedRate(() -> {
                if (executions.incrementAndGet() == 1) {
                    throwUnchecked(new IOException("periodic I/O failed"));
                }
                recoveredExecution.countDown();
            }, 0, 10, TimeUnit.MILLISECONDS);

            assertTrue(recoveredExecution.await(2, TimeUnit.SECONDS),
                    "a recoverable I/O exception must not cancel later executions");
            assertTrue(observedEvents.list.stream().anyMatch(event ->
                            event.getThrowableProxy() != null
                                    && IOException.class.getName().equals(
                                    event.getThrowableProxy().getClassName())),
                    "the I/O failure must reach an observable logger even if a file appender fails");
        } finally {
            executor.shutdownNow();
            logger.detachAppender(failingFileAppender);
            logger.detachAppender(observedEvents);
            logger.setAdditive(previousAdditivity);
            logger.setLevel(previousLevel);
            failingFileAppender.stop();
            observedEvents.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable failure) throws E {
        throw (E) failure;
    }
}
