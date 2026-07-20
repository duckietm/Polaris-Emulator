package com.eu.habbo.messages;

import io.netty.buffer.ByteBuf;
import java.util.Objects;

/**
 * Prepares one packet frame for reuse across broadcast recipients while
 * retaining independent Netty reader indexes.
 */
public final class ServerMessageFrame {

    private ServerMessageFrame() {}

    public static void prepareBroadcast(ServerMessage message) {
        Objects.requireNonNull(message, "message").prepareBroadcast();
    }

    public static boolean isBroadcastPrepared(ServerMessage message) {
        return message != null && message.isBroadcastPrepared();
    }

    public static ByteBuf retainedDuplicate(ServerMessage message) {
        return Objects.requireNonNull(message, "message").retainedDuplicateForBroadcast();
    }
}
