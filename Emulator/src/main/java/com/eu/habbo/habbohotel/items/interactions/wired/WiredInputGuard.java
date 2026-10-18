package com.eu.habbo.habbohotel.items.interactions.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ClientMessage;
import java.util.Arrays;

public final class WiredInputGuard {
    public static final int MAX_INT_PARAMS = 100;
    public static final int MAX_STRING_PARAM_LENGTH = 1024;
    public static final int MAX_ABSOLUTE_FURNI_IDS = 100;
    /** The official editor's action delay: 0 to 120 pulses of 0.5 s. */
    public static final int DEFAULT_MAX_DELAY = 120;

    public static final int MAX_ABSOLUTE_DELAY = 3600;
    /** Source types a box may send per selection kind (the official editor sends one per slot). */
    public static final int MAX_SOURCE_TYPES = 10;
    /** Variables a box may reference. */
    public static final int MAX_VARIABLE_IDS = 20;

    public static final int MAX_VARIABLE_ID_LENGTH = 100;
    public static final int MIN_STUFF_SELECTION_CODE = -1;
    public static final int MAX_STUFF_SELECTION_CODE = 2;

    private WiredInputGuard() {}

    public static int[] readIntParams(ClientMessage packet) {
        int count = packet.readInt();
        if (count < 0 || count > MAX_INT_PARAMS) {
            throw new IllegalArgumentException("Invalid wired int param count");
        }

        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = packet.readInt();
        }
        return values;
    }

    public static String readStringParam(ClientMessage packet) {
        return readStringParam(packet, MAX_STRING_PARAM_LENGTH);
    }

    public static String readStringParam(ClientMessage packet, int maximumLength) {
        String value = packet.readString();
        if (value == null || value.isEmpty()) {
            return "";
        }

        int boundedMaximum = Math.max(1, Math.min(WiredLargePayload.MAX_STRING_PARAM_LENGTH, maximumLength));
        if (value.length() > boundedMaximum) {
            throw new IllegalArgumentException("Wired string parameter exceeds the allowed length");
        }
        return value;
    }

    public static int[] readFurniIds(ClientMessage packet) {
        int count = packet.readInt();
        int maxCount = maxFurniSelectionCount();
        if (count < 0 || count > maxCount) {
            throw new IllegalArgumentException("Invalid wired furni selection count");
        }

        int[] values = new int[count];
        int accepted = 0;
        for (int i = 0; i < count; i++) {
            int itemId = packet.readInt();
            if (itemId > 0) {
                values[accepted++] = itemId;
            }
        }

        return accepted == values.length ? values : Arrays.copyOf(values, accepted);
    }

    /** A furni or user source type list from the official save packet. */
    public static int[] readSourceTypes(ClientMessage packet) {
        int count = packet.readInt();
        if (count < 0 || count > MAX_SOURCE_TYPES) {
            throw new IllegalArgumentException("Invalid wired source type count");
        }

        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = packet.readInt();
        }
        return values;
    }

    /** The variable ids from the official save packet. */
    public static String[] readVariableIds(ClientMessage packet) {
        int count = packet.readInt();
        if (count < 0 || count > MAX_VARIABLE_IDS) {
            throw new IllegalArgumentException("Invalid wired variable id count");
        }

        String[] values = new String[count];
        for (int i = 0; i < count; i++) {
            String value = packet.readString();
            if (value == null) value = "";
            if (value.length() > MAX_VARIABLE_ID_LENGTH) {
                throw new IllegalArgumentException("Wired variable id exceeds the allowed length");
            }
            values[i] = value;
        }
        return values;
    }

    /** Pulses, or a negative precise delay in milliseconds, within the configured maximum time. */
    public static int normalizeDelay(int delay) {
        return com.eu.habbo.habbohotel.wired.core.WiredDelay.normalize(delay, maxDelay());
    }

    public static int normalizeStuffSelectionCode(int code) {
        if (code < MIN_STUFF_SELECTION_CODE || code > MAX_STUFF_SELECTION_CODE) {
            return MIN_STUFF_SELECTION_CODE;
        }

        return code;
    }

    public static int maxFurniSelectionCount() {
        int selectionLimit = Emulator.getConfig() != null
                ? Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 20)
                : 20;
        selectionLimit = Math.max(1, selectionLimit);
        return Math.min(MAX_ABSOLUTE_FURNI_IDS, selectionLimit * 20);
    }

    public static int maxDelay() {
        int configured = Emulator.getConfig() != null
                ? Emulator.getConfig().getInt("hotel.wired.max_delay", DEFAULT_MAX_DELAY)
                : DEFAULT_MAX_DELAY;
        configured = Math.max(0, configured);
        return Math.min(MAX_ABSOLUTE_DELAY, configured);
    }
}
