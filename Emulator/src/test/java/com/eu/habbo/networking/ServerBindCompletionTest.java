package com.eu.habbo.networking;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerBindCompletionTest {

    @Test
    void connectAwaitsBindCompletionWithoutPolling() throws Exception {
        ChannelFuture bindFuture = mock(ChannelFuture.class);
        ChannelFuture closeFuture = mock(ChannelFuture.class);
        Channel channel = mock(Channel.class);
        when(bindFuture.awaitUninterruptibly()).thenReturn(bindFuture);
        when(bindFuture.isDone()).thenReturn(true);
        when(bindFuture.isSuccess()).thenReturn(true);
        when(bindFuture.channel()).thenReturn(channel);
        when(channel.closeFuture()).thenReturn(closeFuture);
        when(channel.close()).thenReturn(closeFuture);
        when(closeFuture.syncUninterruptibly()).thenReturn(closeFuture);

        TestServer server = new TestServer(bindFuture);
        try {
            server.connect();
            verify(bindFuture).awaitUninterruptibly();
        } finally {
            server.stop();
        }
    }

    @Test
    void bindFailurePropagatesAsStartupFailure() throws Exception {
        ChannelFuture bindFuture = mock(ChannelFuture.class);
        IllegalStateException cause =
                new IllegalStateException("address already in use");
        when(bindFuture.awaitUninterruptibly()).thenReturn(bindFuture);
        when(bindFuture.isSuccess()).thenReturn(false);
        when(bindFuture.cause()).thenReturn(cause);

        TestServer server = new TestServer(bindFuture);
        try {
            assertThrows(ServerBindException.class, server::connect);
        } finally {
            server.stop();
        }
    }

    private static final class TestServer extends Server {
        private final ChannelFuture bindFuture;

        private TestServer(ChannelFuture bindFuture) throws Exception {
            super("Test Server", "127.0.0.1", 0, 1, 1);
            this.bindFuture = bindFuture;
        }

        @Override
        protected ChannelFuture bind(
                ServerBootstrap bootstrap, String host, int port) {
            return this.bindFuture;
        }
    }
}
