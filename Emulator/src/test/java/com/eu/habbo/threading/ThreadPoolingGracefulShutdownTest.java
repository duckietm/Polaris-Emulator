package com.eu.habbo.threading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ThreadPoolingGracefulShutdownTest {

    @Test
    void waitsForRunningTasksBeforeForcingShutdown() throws Exception {
        ThreadPooling pooling = new ThreadPooling(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        ExecutorService shutdownCaller = Executors.newSingleThreadExecutor();

        try {
            pooling.run(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));

            Future<ThreadPooling.ShutdownResult> shutdown =
                    shutdownCaller.submit(() -> pooling.shutDown(2, TimeUnit.SECONDS));

            assertThrows(TimeoutException.class, () -> shutdown.get(100, TimeUnit.MILLISECONDS));
            release.countDown();

            ThreadPooling.ShutdownResult result = shutdown.get(2, TimeUnit.SECONDS);
            assertTrue(result.terminatedGracefully());
            assertEquals(0, result.cancelledTasks());
            assertFalse(interrupted.await(50, TimeUnit.MILLISECONDS));
            assertTrue(pooling.getService().isTerminated());
        } finally {
            release.countDown();
            pooling.getService().shutdownNow();
            shutdownCaller.shutdownNow();
        }
    }

    @Test
    void forcesShutdownAfterBoundedTimeoutAndReportsQueuedTasks() throws Exception {
        ThreadPooling pooling = new ThreadPooling(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);

        try {
            pooling.run(() -> {
                started.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            });
            pooling.run(() -> {}, TimeUnit.MINUTES.toMillis(1));
            assertTrue(started.await(1, TimeUnit.SECONDS));

            ThreadPooling.ShutdownResult result = pooling.shutDown(50, TimeUnit.MILLISECONDS);

            assertFalse(result.terminatedGracefully());
            assertTrue(result.terminated());
            assertTrue(result.cancelledTasks() >= 1);
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            assertTrue(pooling.getService().isShutdown());
        } finally {
            pooling.getService().shutdownNow();
        }
    }

    @Test
    void gameEnvironmentDisposesEveryCoreScheduler() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/GameEnvironment.java"));

        assertTrue(source.contains("this.pointsScheduler.setDisposed(true);"));
        assertTrue(source.contains("this.pixelScheduler.setDisposed(true);"));
        assertTrue(source.contains("this.creditsScheduler.setDisposed(true);"));
        assertTrue(source.contains("this.gotwPointsScheduler.setDisposed(true);"));
        assertTrue(source.contains("this.subscriptionScheduler.setDisposed(true);"));
    }

    @Test
    void rejectedDelayedShutdownTaskIsReported() {
        ThreadPooling pooling = new ThreadPooling(1);
        Logger logger = (Logger) LoggerFactory.getLogger(ThreadPooling.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);

        try {
            pooling.setCanAdd(false);

            assertNull(pooling.run(() -> {}, 500));
            assertTrue(events.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("Rejected delayed task during shutdown")));
        } finally {
            logger.detachAppender(events);
            pooling.getService().shutdownNow();
        }
    }
}
