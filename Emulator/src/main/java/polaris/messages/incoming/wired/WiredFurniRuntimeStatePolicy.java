package polaris.messages.incoming.wired;

import java.util.Set;
import polaris.hotel.rooms.BuildersClubPalette;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredInternalVariableSupport;

final class WiredFurniRuntimeStatePolicy {
    static final int ACTION_READ = 0;
    static final int ACTION_WRITE = 1;
    static final int MAX_KEY_LENGTH = 64;
    static final String GRAVITY_KEY = "@gravity";
    static final String OPACITY_KEY = "@opacity";
    private static final Set<String> ALLOWED_KEYS = Set.of(
            GRAVITY_KEY,
            OPACITY_KEY,
            BuildersClubPalette.RGB,
            BuildersClubPalette.RED,
            BuildersClubPalette.GREEN,
            BuildersClubPalette.BLUE);

    private WiredFurniRuntimeStatePolicy() {}

    static Result read(Room room, UserItem item, String key) {
        String normalized = normalizeAllowedKey(key);
        if (room == null
                || item == null
                || normalized.isEmpty()
                || !WiredInternalVariableSupport.canUseFurniReference(normalized)
                || !WiredInternalVariableSupport.hasFurniValue(room, item, normalized)) {
            return Result.unsupported();
        }

        Integer value = WiredInternalVariableSupport.readFurniValue(room, item, normalized);
        return value == null ? Result.unsupported() : new Result(value, true, true);
    }

    static Result write(Room room, UserItem item, String key, int value) {
        String normalized = normalizeAllowedKey(key);
        if (!isValueInRange(normalized, value)
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
        return ALLOWED_KEYS.contains(normalized) ? normalized : "";
    }

    /** Gravity is a flag, opacity a percentage, a colour 0xRRGGBB and its parts 0-255. */
    private static boolean isValueInRange(String normalizedKey, int value) {
        if (OPACITY_KEY.equals(normalizedKey)) {
            return value >= 0 && value <= 100;
        }
        if (BuildersClubPalette.RGB.equals(normalizedKey)) {
            return value >= 0 && value <= 0xFFFFFF;
        }
        if (BuildersClubPalette.isKey(normalizedKey)) {
            return value >= 0 && value <= 0xFF;
        }
        return value == 0 || value == 1;
    }

    record Result(int value, boolean supported, boolean success) {
        static Result unsupported() {
            return new Result(0, false, false);
        }
    }
}
