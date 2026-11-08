package polaris.threading;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import polaris.database.PersistenceExecutor;
import polaris.database.PersistenceOperationMonitor;

class ThreadPoolingPersistenceRoutingTest {

    @Test
    void persistenceTasksUseTheInjectedDatabaseExecutor() {
        PersistenceExecutor persistence = mock(PersistenceExecutor.class);
        ThreadPooling threading = new ThreadPooling(1, persistence);
        Runnable task = () -> {};
        try {
            threading.runPersistence(task);

            verify(persistence).execute(same(task));
        } finally {
            threading.shutDown();
        }
    }

    @Test
    void aRejectedPersistenceTaskStillRunsOnTheGeneralPool() throws Exception {
        PersistenceExecutor persistence = mock(PersistenceExecutor.class);
        doThrow(new RejectedExecutionException("Persistence queue stayed full"))
                .when(persistence)
                .execute(any(Runnable.class));
        ThreadPooling threading = new ThreadPooling(1, persistence);
        CountDownLatch ran = new CountDownLatch(1);
        try {
            threading.runPersistence(ran::countDown);

            assertTrue(ran.await(5, TimeUnit.SECONDS), "the write is not lost");
        } finally {
            threading.shutDown();
        }
    }

    @Test
    void exposesPersistenceOperationMetrics() {
        PersistenceExecutor persistence = new PersistenceExecutor(1, 4);
        ThreadPooling threading = new ThreadPooling(1, persistence);
        try {
            threading.runPersistence(() -> {});
            persistence.shutDown();

            PersistenceOperationMonitor.Snapshot snapshot = threading.getPersistenceOperationSnapshot();
            org.junit.jupiter.api.Assertions.assertEquals(1L, snapshot.submittedCount());
            org.junit.jupiter.api.Assertions.assertEquals(1L, snapshot.succeededCount());
            org.junit.jupiter.api.Assertions.assertEquals(0L, snapshot.activeCount());
        } finally {
            threading.shutDown();
            persistence.shutDown();
        }
    }

    @Test
    void persistenceMetricsComeFromTheInjectedDatabaseExecutor() {
        PersistenceExecutor persistence = mock(PersistenceExecutor.class);
        PersistenceExecutor.Metrics metrics = new PersistenceExecutor.Metrics(1, 2, 3, 2, 4L, 5L, true);
        when(persistence.metrics()).thenReturn(metrics);
        ThreadPooling threading = new ThreadPooling(1, persistence);
        try {
            assertSame(metrics, threading.getPersistenceMetrics());
        } finally {
            threading.shutDown();
        }
    }
}
