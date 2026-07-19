package com.eu.habbo.networking;

import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ServerOutboundBackpressureTest {

    @Test
    void configuresExplicitWriteBufferWaterMarks() throws Exception {
        TestServer server = new TestServer();
        try {
            server.initializePipeline();

            WriteBufferWaterMark waterMark = (WriteBufferWaterMark) server
                    .getServerBootstrap()
                    .config()
                    .childOptions()
                    .get(ChannelOption.WRITE_BUFFER_WATER_MARK);

            assertNotNull(waterMark);
            assertEquals(32 * 1024, waterMark.low());
            assertEquals(64 * 1024, waterMark.high());
        } finally {
            server.stop();
        }
    }

    private static final class TestServer extends Server {
        private TestServer() throws Exception {
            super("Backpressure Test Server", "127.0.0.1", 0, 1, 1);
        }
    }
}
