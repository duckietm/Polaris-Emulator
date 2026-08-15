package com.eu.habbo.networking.gameserver;

import com.eu.habbo.monitoring.ExecutionBackpressureMetrics;
import io.netty.util.concurrent.EventExecutorGroup;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BlockingHttpExecutionGroup {
    private static final int DEFAULT_QUEUE_CAPACITY = 128;
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockingHttpExecutionGroup.class);
    private static final GroupHolder GROUP = new GroupHolder();

    private BlockingHttpExecutionGroup() {}

    static EventExecutorGroup get(int configuredThreads) {
        return get(configuredThreads, DEFAULT_QUEUE_CAPACITY);
    }

    static EventExecutorGroup get(int configuredThreads, int configuredQueueCapacity) {
        return GROUP.get(configuredThreads, configuredQueueCapacity);
    }

    static ExecutionCapacityGate admissionGate() {
        return GROUP.admissionGate();
    }

    static void shutdown() {
        GROUP.shutdown();
    }

    private static final class GroupHolder {
        private EventExecutorGroup group;
        private ExecutionCapacityGate admissionGate;

        private synchronized EventExecutorGroup get(int configuredThreads, int configuredQueueCapacity) {
            if (this.group == null || this.group.isShuttingDown() || this.group.isShutdown()) {
                int threads = configuredThreads > 0 ? configuredThreads : 8;
                int applicationCapacity = BoundedEventExecutorGroups.configuredQueueCapacity(
                        configuredQueueCapacity, DEFAULT_QUEUE_CAPACITY);
                this.admissionGate =
                        new ExecutionCapacityGate(applicationCapacity, ExecutionBackpressureMetrics.Lane.BLOCKING_HTTP);
                this.group = BoundedEventExecutorGroups.create(
                        threads,
                        BoundedEventExecutorGroups.withControlTaskReserve(applicationCapacity),
                        "BlockingHttp",
                        ExecutionBackpressureMetrics.Lane.BLOCKING_HTTP);
            }
            return this.group;
        }

        private synchronized ExecutionCapacityGate admissionGate() {
            if (this.admissionGate == null) {
                throw new IllegalStateException("Blocking HTTP executor has not been initialized");
            }
            return this.admissionGate;
        }

        private synchronized void shutdown() {
            if (this.group == null) {
                return;
            }
            try {
                this.group.shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS).syncUninterruptibly();
            } catch (Exception e) {
                LOGGER.warn("Blocking HTTP group shutdown interrupted", e);
            } finally {
                this.group = null;
                this.admissionGate = null;
            }
        }
    }
}
