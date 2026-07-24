package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;

final class WiredFurniRuntimeStatePolicy {
    static final int ACTION_READ = 0;
    static final int ACTION_WRITE = 1;
    static final int MAX_KEY_LENGTH = 64;
    static final String GRAVITY_KEY = "@gravity";

    private WiredFurniRuntimeStatePolicy() {}

    static Result read(Room room, HabboItem item, String key) {
        String normalized = normalizeAllowedKey(key);
        if (room == null
                || item == null
                || normalized.isEmpty()
                || !WiredInternalVariableSupport.canUseFurniReference(normalized)
                || !WiredInternalVariableSupport.hasFurniValue(item, normalized)) {
            return Result.unsupported();
        }

        Integer value = WiredInternalVariableSupport.readFurniValue(room, item, normalized);
        return value == null ? Result.unsupported() : new Result(value, true, true);
    }

    static Result write(Room room, HabboItem item, String key, int value) {
        String normalized = normalizeAllowedKey(key);
        if ((value != 0 && value != 1)
                || room == null
                || item == null
                || normalized.isEmpty()
                || !WiredInternalVariableSupport.canUseFurniDestination(normalized)
                || !WiredInternalVariableSupport.hasFurniValue(item, normalized)) {
            return Result.unsupported();
        }

        boolean success = WiredInternalVariableSupport.writeFurniValue(room, item, normalized, value);
        Integer current = WiredInternalVariableSupport.readFurniValue(room, item, normalized);
        return new Result(current == null ? 0 : current, true, success);
    }

    static String normalizeAllowedKey(String key) {
        if (key == null || key.length() > MAX_KEY_LENGTH) {
            return "";
        }

        String normalized = WiredInternalVariableSupport.normalizeKey(key);
        return GRAVITY_KEY.equals(normalized) ? normalized : "";
    }

    record Result(int value, boolean supported, boolean success) {
        static Result unsupported() {
            return new Result(0, false, false);
        }
    }
}
