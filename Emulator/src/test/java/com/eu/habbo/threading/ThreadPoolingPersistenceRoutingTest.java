package com.eu.habbo.threading;

import com.eu.habbo.database.PersistenceExecutor;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

class ThreadPoolingPersistenceRoutingTest {

    @Test
    void persistenceTasksUseTheInjectedDatabaseExecutor() {
        PersistenceExecutor persistence =
                mock(PersistenceExecutor.class);
        ThreadPooling threading =
                new ThreadPooling(1, persistence);
        Runnable task = () -> {
        };
        try {
            threading.runPersistence(task);

            verify(persistence).execute(same(task));
        } finally {
            threading.shutDown();
        }
    }
}
