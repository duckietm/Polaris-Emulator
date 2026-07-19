package com.eu.habbo.networking.gameserver;

import com.eu.habbo.Emulator;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

final class BlockingHttpExecutionGroup {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(BlockingHttpExecutionGroup.class);
    private static final EventExecutorGroup GROUP = new DefaultEventExecutorGroup(
            configuredThreads(),
            new DefaultThreadFactory("BlockingHttp", true));

    private BlockingHttpExecutionGroup() {
    }

    static EventExecutorGroup get() {
        return GROUP;
    }

    static void shutdown() {
        try {
            GROUP.shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS)
                    .syncUninterruptibly();
        } catch (Exception e) {
            LOGGER.warn("Blocking HTTP group shutdown interrupted", e);
        }
    }

    static int configuredThreads() {
        int fallback = 8;
        if (Emulator.getConfig() == null) {
            return fallback;
        }

        int configured = Emulator.getConfig().getInt(
                "http.blocking.pool.size", fallback);
        return configured > 0 ? configured : fallback;
    }
}
