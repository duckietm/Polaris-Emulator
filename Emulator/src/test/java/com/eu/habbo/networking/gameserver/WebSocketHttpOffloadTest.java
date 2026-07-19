package com.eu.habbo.networking.gameserver;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.EventExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class WebSocketHttpOffloadTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void blockingHttpHandlersLeaveTheSocketEventLoopAndKeepChannelOrdering()
            throws Exception {
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        Object previousConfig = configField.get(null);
        Path configFile = this.temporaryDirectory.resolve("config.ini");
        Files.writeString(
                configFile,
                "nitro.secure.master_key=test-key\n"
                        + "nitro.secure.assets.enabled=true\n"
                        + "crypto.ws.enabled=false\n");
        configField.set(null, new ConfigurationManager(configFile.toString()));

        EventLoopGroup eventLoops =
                new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        NioSocketChannel channel = new NioSocketChannel();
        try {
            eventLoops.register(channel).syncUninterruptibly();
            new WebSocketChannelInitializer().initChannel(channel);

            EventExecutor socketExecutor = channel.eventLoop();
            EventExecutor blockingExecutor =
                    channel.pipeline().context("nitroSecureAssetHandler").executor();

            assertNotSame(socketExecutor, blockingExecutor);
            assertSame(
                    blockingExecutor,
                    channel.pipeline().context("badgeHttpHandler").executor());
            assertSame(
                    blockingExecutor,
                    channel.pipeline().context("badgeLeaderboardHttpHandler").executor());
            assertSame(
                    blockingExecutor,
                    channel.pipeline().context("emuStatsHttpHandler").executor());
            assertSame(
                    socketExecutor,
                    channel.pipeline().context("wsHttpHandler").executor());
            assertSame(
                    socketExecutor,
                    channel.pipeline().context("authHttpHandler").executor());
        } finally {
            channel.close().syncUninterruptibly();
            eventLoops.shutdownGracefully().syncUninterruptibly();
            configField.set(null, previousConfig);
        }
    }
}
