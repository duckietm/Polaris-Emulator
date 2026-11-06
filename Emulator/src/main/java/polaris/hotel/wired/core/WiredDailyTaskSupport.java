package polaris.hotel.wired.core;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.extra.WiredExtraDailyTask;
import polaris.hotel.items.interactions.wired.extra.WiredExtraUserVariable;
import polaris.hotel.rooms.Room;

/**
 * The daily reset of a user counter that shares its tile with a {@link WiredExtraDailyTask}. Nothing
 * runs at midnight: a value last written before the current day began, on the room's wired clock,
 * simply reads as 0, and the next write starts the new day. Stored rows keep their write time, so the
 * rule holds for holders who were away and across restarts.
 */
public final class WiredDailyTaskSupport {
    private WiredDailyTaskSupport() {}

    public static boolean isDailyCounter(Room room, InteractionWiredExtra definition) {
        if (room == null
                || room.getRoomSpecialTypes() == null
                || !(definition instanceof WiredExtraUserVariable userVariable)
                || userVariable.isArray()) {
            return false;
        }

        Collection<InteractionWiredExtra> extras =
                room.getRoomSpecialTypes().getExtras(definition.getX(), definition.getY());
        if (extras == null) {
            return false;
        }

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraDailyTask) {
                return true;
            }
        }

        return false;
    }

    /** The unix second at which the day containing {@code nowSeconds} began in the room's wired timezone. */
    public static long dayStart(Room room, long nowSeconds) {
        ZoneId zone = WiredRoomTime.zoneFor(room);
        return Instant.ofEpochSecond(nowSeconds)
                .atZone(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toEpochSecond();
    }

    public static boolean isExpired(Room room, InteractionWiredExtra definition, int updatedAt, int nowSeconds) {
        return nowSeconds > 0 && isDailyCounter(room, definition) && updatedAt < dayStart(room, nowSeconds);
    }

    /** What a stored value reads as today. */
    public static Integer effectiveValue(
            Room room, InteractionWiredExtra definition, Integer value, int updatedAt, int nowSeconds) {
        if (value == null || value == 0) {
            return value;
        }

        return isExpired(room, definition, updatedAt, nowSeconds) ? Integer.valueOf(0) : value;
    }
}
