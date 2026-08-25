package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.messages.ServerMessage;

/** Serializes chest furni rows using the official client-6 {@code ChestStorage} wire shape. */
public final class ChestFurniWireUtil {
    public static final int LEGACY_FORMAT = 0;
    private static final int UNIQUE_FLAG = 256;

    private ChestFurniWireUtil() {
    }

    public static void appendStoredItem(ServerMessage message, ChestFurniStoredItem item) {
        message.appendInt(item.inventoryId);
        message.appendInt(item.lockState);
        appendLong(message, item.transactionId);
        message.appendBoolean(item.wallItem);
        message.appendInt(item.wireTypeId());
        message.appendString(item.legacyPosterId == null ? "" : item.legacyPosterId);
        message.appendBoolean(item.groupable);
        message.appendInt(item.specialType);
        appendStuffData(message, item);
        if (!item.wallItem) {
            message.appendInt(item.extra);
        }
    }

    private static void appendStuffData(ServerMessage message, ChestFurniStoredItem item) {
        int flags = 0;
        if (item.limitedStack > 0) {
            flags |= UNIQUE_FLAG;
        }
        message.appendInt((flags & 0xFF00) | (item.stuffDataFormat & 0xFF));
        message.appendString(item.extradata == null ? "" : item.extradata);
        if ((flags & UNIQUE_FLAG) != 0) {
            message.appendInt(item.limitedSells);
            message.appendInt(item.limitedStack);
        }
    }

    /** Flash {@code readLong()} wire shape (two big-endian ints). */
    private static void appendLong(ServerMessage message, long value) {
        message.appendInt((int) (value >>> 32));
        message.appendInt((int) value);
    }
}
