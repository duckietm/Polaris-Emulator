package com.eu.habbo.networking.rconserver;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RCONServerHandlerWriteTest {

    @Test
    void responseUsesItsExactUtf8Bytes() {
        ByteBuf response = RCONServerHandler.responseBuffer("{\"message\":\"på gensyn\"}");
        try {
            assertEquals("{\"message\":\"på gensyn\"}", response.toString(StandardCharsets.UTF_8));
        } finally {
            response.release();
        }
    }
}
