package com.eu.habbo.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.eu.habbo.networking.gameserver.encoders.GameServerMessageEncoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ServerMessageFrameCompatibilityTest {

    @Test
    void getReturnsAnIndependentDefensiveCopy() {
        ServerMessage message = message();
        ByteBuf first = message.get();
        ByteBuf second = message.get();
        try {
            int original = second.getByte(8);
            first.setByte(8, original + 1);

            assertNotEquals(first.getByte(8), second.getByte(8));
            ByteBuf third = message.get();
            try {
                assertEquals(original, third.getByte(8));
            } finally {
                third.release();
            }
        } finally {
            first.release();
            second.release();
        }
    }

    @Test
    void appendingAfterGetReframesWithoutMutatingOlderCopy() {
        ServerMessage message = new ServerMessage(321);
        message.appendInt(7);
        ByteBuf before = message.get();
        try {
            int beforeLength = before.getInt(0);
            message.appendString("later");
            ByteBuf after = message.get();
            try {
                assertEquals(beforeLength, before.getInt(0));
                assertEquals(after.readableBytes() - 4, after.getInt(0));
                assertEquals(beforeLength + 7, after.getInt(0));
            } finally {
                after.release();
            }
        } finally {
            before.release();
        }
    }

    @Test
    void encoderWritesTheExactEstablishedFrame() {
        ServerMessage message = message();
        ByteBuf expected = message.get();
        EmbeddedChannel channel = new EmbeddedChannel(new GameServerMessageEncoder());
        try {
            channel.writeOutbound(message);
            ByteBuf encoded = channel.readOutbound();
            try {
                assertEquals(expected, encoded);
            } finally {
                encoded.release();
            }
        } finally {
            expected.release();
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void concurrentReadsKeepLengthAndPayloadStable() throws Exception {
        ServerMessage message = message();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> reads = new ArrayList<>();
            for (int task = 0; task < 32; task++) {
                reads.add(() -> {
                    for (int iteration = 0; iteration < 100; iteration++) {
                        ByteBuf frame = message.get();
                        try {
                            assertEquals(frame.readableBytes() - 4, frame.getInt(0));
                            assertEquals(4321, frame.getUnsignedShort(4));
                            assertEquals(99, frame.getInt(6));
                        } finally {
                            frame.release();
                        }
                    }
                    return null;
                });
            }

            for (Future<Void> result : executor.invokeAll(reads)) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static ServerMessage message() {
        ServerMessage message = new ServerMessage(4321);
        message.appendInt(99);
        message.appendString("broadcast");
        return message;
    }
}
