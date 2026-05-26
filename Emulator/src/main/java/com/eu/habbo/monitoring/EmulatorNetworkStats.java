package com.eu.habbo.monitoring;

import java.util.concurrent.atomic.AtomicLong;

public final class EmulatorNetworkStats {
    private static final AtomicLong INCOMING_PACKETS = new AtomicLong();
    private static final AtomicLong OUTGOING_PACKETS = new AtomicLong();
    private static final AtomicLong INCOMING_BYTES = new AtomicLong();
    private static final AtomicLong OUTGOING_BYTES = new AtomicLong();

    private EmulatorNetworkStats() {
    }

    public static void recordIncoming(int byteCount) {
        INCOMING_PACKETS.incrementAndGet();
        if (byteCount > 0) {
            INCOMING_BYTES.addAndGet(byteCount);
        }
    }

    public static void recordOutgoing(int byteCount) {
        OUTGOING_PACKETS.incrementAndGet();
        if (byteCount > 0) {
            OUTGOING_BYTES.addAndGet(byteCount);
        }
    }

    public static long getIncomingPackets() {
        return INCOMING_PACKETS.get();
    }

    public static long getOutgoingPackets() {
        return OUTGOING_PACKETS.get();
    }

    public static long getIncomingBytes() {
        return INCOMING_BYTES.get();
    }

    public static long getOutgoingBytes() {
        return OUTGOING_BYTES.get();
    }
}
