package com.eu.habbo.networking.gameserver;

import com.eu.habbo.monitoring.ExecutionBackpressureMetrics;
import io.netty.util.concurrent.EventExecutorGroup;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BlockingHttpExecutionGroup {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockingHttpExecutionGroup.class);
    private static final GroupHolder GROUP = new GroupHolder();

    private BlockingHttpExecutionGroup() {}

    static EventExecutorGroup get(int configuredThreads) {
        return GROUP.get(configuredThreads, ExecutionBackpressureSettings.defaults());
    }

    static EventExecutorGroup get(int configuredThreads, ExecutionBackpressureSettings settings) {
        return GROUP.get(configuredThreads, settings);
    }

    static ExecutionAdmissionHandler admissionHandler(String targetHandlerName) {
        return GROUP.admissionHandler(targetHandlerName);
    }

    static ExecutionCapacityController.Snapshot capacitySnapshot() {
        return GROUP.state().controller.snapshot();
    }

    static ExecutionBackpressureMetrics.Snapshot metricsSnapshot() {
        return GROUP.state().metrics.snapshot();
    }

    static ExecutionBackpressureStatus.Snapshot statusSnapshot() {
        return GROUP.statusSnapshot();
    }

    static void shutdown() {
        GROUP.shutdown();
    }

    private static final class GroupHolder {
        private State current;

        private synchronized EventExecutorGroup get(int configuredThreads, ExecutionBackpressureSettings settings) {
            if (this.current == null || this.current.executor.isShuttingDown() || this.current.executor.isShutdown()) {
                int threads = configuredThreads > 0 ? configuredThreads : 8;
                ExecutionCapacityController controller = new ExecutionCapacityController(
                        settings.httpCapacity(),
                        Math.max(0, (int) (((long) settings.httpCapacity() * 3L) / 4L)),
                        settings.mode());
                ExecutionBackpressureMetrics metrics = new ExecutionBackpressureMetrics("http");
                EventExecutorGroup executor = BoundedEventExecutorGroups.create(
                        threads, settings.httpCapacity(), "BlockingHttp", settings.mode());
                this.current = new State(executor, controller, metrics);
            }
            return this.current.executor;
        }

        private synchronized State state() {
            if (this.current == null) {
                get(8, ExecutionBackpressureSettings.defaults());
            }
            return this.current;
        }

        private synchronized ExecutionAdmissionHandler admissionHandler(String targetHandlerName) {
            State state = state();
            return ExecutionAdmissionHandler.forBlockingHttp(targetHandlerName, state.controller, state.metrics);
        }

        private synchronized void shutdown() {
            if (this.current == null) {
                return;
            }
            try {
                this.current
                        .executor
                        .shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS)
                        .syncUninterruptibly();
            } catch (Exception e) {
                LOGGER.warn("Blocking HTTP group shutdown interrupted", e);
            } finally {
                this.current = null;
            }
        }

        private synchronized ExecutionBackpressureStatus.Snapshot statusSnapshot() {
            if (this.current == null) {
                return ExecutionBackpressureStatus.Snapshot.inactive();
            }
            return ExecutionBackpressureStatus.combine(
                    this.current.controller.snapshot(), this.current.metrics.snapshot());
        }
    }

    private record State(
            EventExecutorGroup executor,
            ExecutionCapacityController controller,
            ExecutionBackpressureMetrics metrics) {}
}
