package com.eu.habbo.messages;

public final class ClientMessageDispatchTiming {

    private ClientMessageDispatchTiming() {}

    public static void markEnqueued(ClientMessage message, long enqueuedAtNanos) {
        message.dispatchEnqueuedAtNanos = enqueuedAtNanos;
    }

    public static long takeEnqueuedAt(ClientMessage message) {
        long enqueuedAt = message.dispatchEnqueuedAtNanos;
        message.dispatchEnqueuedAtNanos = 0L;
        return enqueuedAt;
    }
}
