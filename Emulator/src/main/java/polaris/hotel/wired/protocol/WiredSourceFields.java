package polaris.hotel.wired.protocol;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads a box's furni and user source, for the official definition's {@code furniSources} and
 * {@code userSources}. Our boxes keep them in fields named {@code furniSource} and {@code userSource}
 * ({@code botSource} on bot boxes, whose bots are the users of the official client); the values already
 * use the official numbering (0 trigger, 11 clicked user, 100 picks, 200 selector, 201 signal).
 *
 * <p>Read only: the boxes still save their sources from their int params, until each editor writes the
 * official fields itself.
 */
public final class WiredSourceFields {
    private static final int[] NONE = new int[0];
    private static final Map<Class<?>, Field[]> FIELDS = new ConcurrentHashMap<>();

    private WiredSourceFields() {}

    /** The box's furni sources, one per furni slot; empty for a box without one. */
    public static int[] furniSources(Object box) {
        return read(box, fields(box)[0]);
    }

    /** The box's user sources, one per user slot; empty for a box without one. */
    public static int[] userSources(Object box) {
        return read(box, fields(box)[1]);
    }

    private static int[] read(Object box, Field field) {
        if (field == null) return NONE;
        try {
            return new int[] {field.getInt(box)};
        } catch (IllegalAccessException e) {
            return NONE;
        }
    }

    private static Field[] fields(Object box) {
        if (box == null) return new Field[2];
        return FIELDS.computeIfAbsent(box.getClass(), type -> {
            Field user = find(type, "userSource");
            return new Field[] {find(type, "furniSource"), user != null ? user : find(type, "botSource")};
        });
    }

    private static Field find(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!field.getName().equals(name)) continue;
                if (field.getType() != int.class) return null;
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }
}
