package polaris.hotel.rooms.variables;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import polaris.hotel.items.interactions.wired.extra.WiredExtraUserVariable;
import polaris.hotel.rooms.Room;

/**
 * The permanent variables of one holder, as the creator tools' holder detail window reads and changes them: the user
 * variables a holder keeps after leaving the room. It answers for a holder who is in the room and for one who is
 * not, so a saved value can be managed while its holder is away.
 */
public final class PermanentUserVariables {
    /** The official actions of a change: set the value of a held variable, give the variable, take it away. */
    public static final int ACTION_SET = 0;

    public static final int ACTION_CREATE = 1;
    public static final int ACTION_DELETE = 2;

    /** One held variable; times are milliseconds, 0 when unknown. */
    public record Entry(String variableId, int value, long createdAt, long updatedAt) {}

    /** A holder and the permanent variables it holds; name and figure are empty when unknown. */
    public record Listing(int holderKey, String name, String figure, List<Entry> entries) {}

    private PermanentUserVariables() {}

    public static Listing of(Room room, int holderKey) {
        RoomUserVariableStore store = room.getUserVariableManager().getStore();
        List<Integer> permanent = new ArrayList<>();

        for (WiredExtraUserVariable definition : room.getUserVariableManager().getDefinitions()) {
            if (definition != null && store.isSaved(definition.getId())) {
                permanent.add(definition.getId());
            }
        }

        List<Entry> entries = new ArrayList<>();

        if (UserVariableHolders.isValid(holderKey) && !permanent.isEmpty()) {
            Map<Integer, RoomUserVariableStore.Value> values = store.all(holderKey, permanent);

            for (Integer definitionId : permanent) {
                RoomUserVariableStore.Value value = values.get(definitionId);

                if (value != null) {
                    entries.add(new Entry(
                            RoomWiredVariableCatalog.variableId(RoomWiredVariableCatalog.TARGET_USER, definitionId),
                            value.value() != null ? value.value() : 0,
                            value.createdAt() * 1000L,
                            value.updatedAt() * 1000L));
                }
            }
        }

        return new Listing(holderKey, name(room, store, holderKey), figure(store, holderKey), entries);
    }

    /** Applies one change; false when the variable is not a permanent user variable or the holder state forbids it. */
    public static boolean apply(Room room, int holderKey, String variableId, int value, int action) {
        if (!UserVariableHolders.isValid(holderKey)
                || RoomWiredVariableCatalog.targetOf(variableId) != RoomWiredVariableCatalog.TARGET_USER) {
            return false;
        }

        int definitionId = RoomWiredVariableCatalog.definitionIdOf(variableId);
        RoomUserVariableStore store = room.getUserVariableManager().getStore();

        if (definitionId <= 0 || !store.isSaved(definitionId)) {
            return false;
        }

        return switch (action) {
            case ACTION_SET -> store.update(holderKey, definitionId, value) == RoomUserVariableStore.Write.WRITTEN;
            case ACTION_CREATE ->
                store.get(holderKey, definitionId) == null
                        && store.put(holderKey, definitionId, value) == RoomUserVariableStore.Write.WRITTEN;
            case ACTION_DELETE -> store.remove(holderKey, definitionId) == RoomUserVariableStore.Write.WRITTEN;
            default -> false;
        };
    }

    private static String name(Room room, RoomUserVariableStore store, int holderKey) {
        if (!UserVariableHolders.isValid(holderKey)) {
            return "";
        }

        String name = UserVariableHolders.kindOf(holderKey) == UserVariableHolders.Kind.USER
                ? store.name(holderKey)
                : UserVariableHolders.nameOf(room, holderKey);

        return name != null ? name : "";
    }

    private static String figure(RoomUserVariableStore store, int holderKey) {
        if (!UserVariableHolders.isValid(holderKey)
                || UserVariableHolders.kindOf(holderKey) != UserVariableHolders.Kind.USER) {
            return "";
        }

        String figure = store.figures(List.of(holderKey)).get(holderKey);
        return figure != null ? figure : "";
    }
}
