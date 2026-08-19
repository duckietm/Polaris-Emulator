package com.eu.habbo.networking.gameserver;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutorGroup;
import java.util.concurrent.RejectedExecutionException;

final class BoundedEventExecutorGroups {
    private static final int MINIMUM_PENDING_TASKS = 16;
    private static final int CONTROL_TASK_RESERVE = 64;

    private BoundedEventExecutorGroups() {}

    static EventExecutorGroup create(
            int threads, int applicationCapacity, String threadPrefix, ExecutionCapacityController.Mode mode) {
        if (threads <= 0) {
            throw new IllegalArgumentException("Executor thread count must be positive");
        }
        if (applicationCapacity <= 0) {
            throw new IllegalArgumentException("Application capacity must be positive");
        }

        DefaultThreadFactory threadFactory = new DefaultThreadFactory(threadPrefix, true);
        if (mode == ExecutionCapacityController.Mode.OBSERVE) {
            return new DefaultEventExecutorGroup(threads, threadFactory);
        }

        int pendingTasks = Math.max(MINIMUM_PENDING_TASKS, withControlTaskReserve(applicationCapacity));
        return new DefaultEventExecutorGroup(threads, threadFactory, pendingTasks, (task, executor) -> {
            throw new RejectedExecutionException(threadPrefix + " execution queue is full");
        });
    }

    private static int withControlTaskReserve(int applicationCapacity) {
        if (applicationCapacity > Integer.MAX_VALUE - CONTROL_TASK_RESERVE) {
            return Integer.MAX_VALUE;
        }
        return applicationCapacity + CONTROL_TASK_RESERVE;
    }
}
