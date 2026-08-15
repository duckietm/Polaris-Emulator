package com.eu.habbo.threading;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

import com.eu.habbo.database.PersistenceExecutor;
import com.eu.habbo.database.PersistenceOperationMonitor;
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
}
