package com.eu.habbo.networking.gameserver;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutorGroup;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BlockingHttpExecutionGroup {
    private static final Logger LOGGER = LoggerFactory.getLogger(BlockingHttpExecutionGroup.class);
    private static final GroupHolder GROUP = new GroupHolder();

    private BlockingHttpExecutionGroup() {}

    static EventExecutorGroup get(int configuredThreads) {
        return GROUP.get(configuredThreads);
    }

    static void shutdown() {
        GROUP.shutdown();
    }

    private static final class GroupHolder {
        private EventExecutorGroup group;

        private synchronized EventExecutorGroup get(int configuredThreads) {
            if (this.group == null || this.group.isShuttingDown() || this.group.isShutdown()) {
                int threads = configuredThreads > 0 ? configuredThreads : 8;
                this.group = new DefaultEventExecutorGroup(threads, new DefaultThreadFactory("BlockingHttp", true));
            }
            return this.group;
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
            }
        }
    }
}
