package com.eu.habbo.threading;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.database.PersistenceExecutor;
import org.junit.jupiter.api.Test;

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
