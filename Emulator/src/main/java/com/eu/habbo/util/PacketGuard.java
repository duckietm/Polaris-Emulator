package com.eu.habbo.util;

import com.eu.habbo.messages.ClientMessage;

public final class PacketGuard {
    private PacketGuard() {
    }

    public static boolean isCountInRange(int count, int min, int max) {
        return count >= min && count <= max;
    }

    public static boolean hasReadableBytes(ClientMessage packet, int requiredBytes) {
        return packet != null && requiredBytes >= 0 && packet.bytesAvailable() >= requiredBytes;
    }

    public static boolean hasFixedWidthEntries(ClientMessage packet, int entryCount, int bytesPerEntry) {
        if (packet == null || entryCount < 0 || bytesPerEntry < 0) {
            return false;
        }

        long requiredBytes = (long) entryCount * bytesPerEntry;
        return requiredBytes <= Integer.MAX_VALUE && packet.bytesAvailable() >= requiredBytes;
    }

    /**
     * Validates a packet-supplied count of 4-byte (int) entries before it is used
     * to allocate a collection or drive repeated reads: the count must be within
     * {@code [0, maxCount]} and the packet must still hold {@code count * 4} bytes.
     */
    public static boolean isValidIntList(int count, int bytesAvailable, int maxCount) {
        if (count < 0 || count > maxCount || bytesAvailable < 0) {
            return false;
        }

        return (long) count * Integer.BYTES <= bytesAvailable;
    }
}
