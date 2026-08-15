package com.eu.habbo.networking.gameserver;

import com.eu.habbo.monitoring.ExecutionBackpressureMetrics;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutorGroup;
import java.util.concurrent.RejectedExecutionException;

final class BoundedEventExecutorGroups {
    static final int MINIMUM_QUEUE_CAPACITY = 16;
    private static final int CONTROL_TASK_RESERVE = 64;

    private BoundedEventExecutorGroups() {}

    static EventExecutorGroup create(
            int threads, int queueCapacity, String threadPrefix, ExecutionBackpressureMetrics.Lane lane) {
        if (threads <= 0) {
            throw new IllegalArgumentException("Executor thread count must be positive");
        }
        if (queueCapacity < MINIMUM_QUEUE_CAPACITY) {
            throw new IllegalArgumentException("Executor queue capacity must be at least " + MINIMUM_QUEUE_CAPACITY);
        }

        return new DefaultEventExecutorGroup(
                threads, new DefaultThreadFactory(threadPrefix, true), queueCapacity, (task, executor) -> {
                    ExecutionBackpressureMetrics.recordRejection(lane);
                    throw new RejectedExecutionException(lane + " execution queue is full");
                });
    }

    static int configuredQueueCapacity(int configured, int fallback) {
        int selected = configured > 0 ? configured : fallback;
        return Math.max(MINIMUM_QUEUE_CAPACITY, selected);
    }

    static int withControlTaskReserve(int applicationCapacity) {
        if (applicationCapacity > Integer.MAX_VALUE - CONTROL_TASK_RESERVE) {
            return Integer.MAX_VALUE;
        }
        return applicationCapacity + CONTROL_TASK_RESERVE;
    }
}
