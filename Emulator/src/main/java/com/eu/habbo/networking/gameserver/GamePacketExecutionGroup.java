package com.eu.habbo.networking.gameserver;

import com.eu.habbo.Emulator;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

final class GamePacketExecutionGroup {
    private static final Logger LOGGER = LoggerFactory.getLogger(GamePacketExecutionGroup.class);
    private static final EventExecutorGroup GROUP = new DefaultEventExecutorGroup(
            configuredThreads(),
            new DefaultThreadFactory("GamePacketHandler", true));

    private GamePacketExecutionGroup() {
    }

    static EventExecutorGroup get() {
        return GROUP;
    }

    static void shutdown() {
        try {
            GROUP.shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS).syncUninterruptibly();
        } catch (Exception e) {
            LOGGER.warn("Packet handler group shutdown interrupted", e);
        }
    }

    static int configuredThreads() {
        int fallback = Math.max(16, Runtime.getRuntime().availableProcessors() * 2);
        if (Emulator.getConfig() == null) {
            return fallback;
        }

        int configured = Emulator.getConfig().getInt("io.packet.handler.threads", fallback);
        return configured > 0 ? configured : fallback;
    }
}
