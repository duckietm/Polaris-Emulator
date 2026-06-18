package com.eu.habbo.habbohotel.items.interactions.wired;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.ClientMessage;

import java.util.Arrays;

public final class WiredInputGuard {
    public static final int MAX_INT_PARAMS = 100;
    public static final int MAX_STRING_PARAM_LENGTH = 1024;
    public static final int MAX_ABSOLUTE_FURNI_IDS = 100;
    public static final int DEFAULT_MAX_DELAY = 20;
    public static final int MAX_ABSOLUTE_DELAY = 3600;
    public static final int MIN_STUFF_SELECTION_CODE = -1;
    public static final int MAX_STUFF_SELECTION_CODE = 2;

    private WiredInputGuard() {
    }

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
        String value = packet.readString();
        if (value == null || value.isEmpty()) {
            return "";
        }

        return value.length() > MAX_STRING_PARAM_LENGTH
                ? value.substring(0, MAX_STRING_PARAM_LENGTH)
                : value;
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

    public static int normalizeDelay(int delay) {
        return Math.max(0, Math.min(delay, maxDelay()));
    }

    public static int normalizeStuffSelectionCode(int code) {
        if (code < MIN_STUFF_SELECTION_CODE || code > MAX_STUFF_SELECTION_CODE) {
            return MIN_STUFF_SELECTION_CODE;
        }

        return code;
    }

    public static int maxFurniSelectionCount() {
        int selectionLimit = Emulator.getConfig() != null
                ? Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 5)
                : 5;
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
