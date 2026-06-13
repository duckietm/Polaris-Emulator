package com.eu.habbo.networking.rconserver;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RCONServerHandlerContractTest {
    private static String handlerSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/networking/rconserver/RCONServerHandler.java"));
    }

    @Test
    void inboundByteBufIsReleasedFromFinallyBlock() throws Exception {
        String source = handlerSource();
        int finallyIndex = source.indexOf("finally");
        int releaseIndex = source.indexOf("data.release()");

        assertTrue(finallyIndex >= 0, "RCON channelRead must release inbound ByteBufs from a finally block");
        assertTrue(releaseIndex > finallyIndex, "RCON channelRead must release the inbound ByteBuf after finally starts");
    }
}
