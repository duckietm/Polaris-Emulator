package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableAddonImport;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableAddonShare;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableAddonSharing;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableFx;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableTextConnector;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.variablefx.WiredVariableFxSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The official variable add-on sharing (Share 1003 / Import 1004). The add-ons stacked with a shared user or room
 * variable reach a room that references it when a Share box on the variable's tile and an Import box on the
 * reference's tile both tick the add-on's category:
 *
 * <ul>
 *   <li>{@link #TEXT_CONNECTORS}: text connectors (1000), after the reference's own connectors;
 *   <li>{@link #SUBVARIABLE_CREATORS}: level-up (1001) and time utilities (1002), when the reference has none;
 *   <li>{@link #VARIABLE_FX}: the user variable fx (1200..1205), drawn by the referencing room's fx service.
 * </ul>
 *
 * <p>Only a source room of the same owner is read. A loaded one is read live from its concurrent wired index, never
 * under its locks, so two rooms reading each other cannot deadlock; one that is not loaded is read from its saved furni
 * once and cached ({@link WiredUnloadedAddonSources}). Work for other rooms goes to the pool.
 */
public final class WiredVariableAddonSharing {
    public static final int TEXT_CONNECTORS = 0;
    public static final int SUBVARIABLE_CREATORS = 1;
    public static final int VARIABLE_FX = 2;

    /** Rooms one Synchronize press refreshes at most. */
    public static final int MAX_SYNCHRONIZED_ROOMS = 50;

    private WiredVariableAddonSharing() {}

    /**
     * An fx box of another room drawn here, over the reference that imports it. {@code variableIds} maps the source
     * room's variable ids this room references (the box's range overrides and audience name those) to the ids of
     * this room's references.
     */
    public record ImportedFx(
            WiredExtraVariableFx box, WiredExtraVariableReference reference, Map<Integer, Integer> variableIds) {
        public ImportedFx {
            variableIds = Map.copyOf(variableIds);
        }

        public ImportedFx(WiredExtraVariableFx box, WiredExtraVariableReference reference) {
            this(box, reference, Map.of());
        }

        /** This room's id of a source room variable; the source id itself when this room does not reference it. */
        public int localId(int sourceItemId) {
            return this.variableIds.getOrDefault(sourceItemId, sourceItemId);
        }
    }

    /** A shared variable box and the boxes on its tile, in execution order. */
    record SourceStack(InteractionWiredExtra source, List<InteractionWiredExtra> tile) {}

    /**
     * The add-ons of {@code category} that {@code definition}, a variable reference in {@code room}, takes over from
     * its source variable, in the source's execution order. Empty for anything else.
     */
    public static List<InteractionWiredExtra> importedAddons(
            Room room, InteractionWiredExtra definition, int category) {
        if (!(definition instanceof WiredExtraVariableReference reference) || !imports(room, reference, category)) {
            return Collections.emptyList();
        }

        return sharedAddons(sourceStack(room, reference), category);
    }

    /** The first imported add-on of {@code category} that is a {@code type}, or null. */
    public static <T extends InteractionWiredExtra> T importedAddon(
            Room room, InteractionWiredExtra definition, int category, Class<T> type) {
        for (InteractionWiredExtra extra : importedAddons(room, definition, category)) {
            if (type.isInstance(extra)) return type.cast(extra);
        }
        return null;
    }

    /** Whether an Import box on the definition's tile ticks {@code category}. */
    public static boolean imports(Room room, InteractionWiredExtra definition, int category) {
        return hasBox(room, definition, WiredExtraVariableAddonImport.class, category);
    }

    /** Whether a Share box on the definition's tile ticks {@code category}. */
    public static boolean shares(Room room, InteractionWiredExtra definition, int category) {
        return hasBox(room, definition, WiredExtraVariableAddonShare.class, category);
    }

    /**
     * The add-ons of {@code category} stacked with the reference's source variable in {@code sourceRoom}, when a Share
     * box there ticks the category.
     */
    static List<InteractionWiredExtra> sharedAddons(
            Room sourceRoom, WiredExtraVariableReference reference, int category) {
        return sharedAddons(liveStack(sourceRoom, reference), category);
    }

    /** The add-ons of {@code category} in a source stack, when a Share box in it ticks the category. */
    static List<InteractionWiredExtra> sharedAddons(SourceStack stack, int category) {
        if (stack == null) return Collections.emptyList();

        boolean shared = false;
        for (InteractionWiredExtra extra : stack.tile()) {
            if (extra instanceof WiredExtraVariableAddonShare share && share.allows(category)) {
                shared = true;
                break;
            }
        }
        if (!shared) return Collections.emptyList();

        List<InteractionWiredExtra> addons = new ArrayList<>();
        for (InteractionWiredExtra extra : stack.tile()) {
            if (inCategory(extra, category)) addons.add(extra);
        }
        return addons;
    }

    /** The shared variable box a reference points at, while it is still there and still shared. */
    static InteractionWiredExtra sourceDefinition(Room sourceRoom, WiredExtraVariableReference reference) {
        if (sourceRoom == null || reference == null || sourceRoom.getRoomSpecialTypes() == null) return null;

        return sharedSource(sourceRoom.getRoomSpecialTypes().getExtra(reference.getSourceVariableItemId()), reference);
    }

    /** {@code extra} when it is the shared user or room variable the reference expects, else null. */
    static InteractionWiredExtra sharedSource(InteractionWiredExtra extra, WiredExtraVariableReference reference) {
        if (reference.isUserReference() && extra instanceof WiredExtraUserVariable variable) {
            return variable.isSharedAvailability() ? variable : null;
        }
        if (reference.isRoomReference() && extra instanceof WiredExtraRoomVariable variable) {
            return variable.isSharedAvailability() ? variable : null;
        }
        return null;
    }

    /**
     * The reference's source stack: live from a loaded source room, else from the source room's saved furni. Null
     * when there is none, the variable is no longer shared or the source room has another owner.
     */
    static SourceStack sourceStack(Room room, WiredExtraVariableReference reference) {
        if (room == null || reference.getSourceRoomId() <= 0 || reference.getSourceRoomId() == room.getId()) {
            return null;
        }

        Room source = loadedRoom(reference.getSourceRoomId());
        if (source != null) {
            return source.getOwnerId() == room.getOwnerId() ? liveStack(source, reference) : null;
        }

        WiredUnloadedAddonSources.Saved saved =
                WiredUnloadedAddonSources.get(reference.getSourceRoomId(), reference.getSourceVariableItemId());
        if (saved.ownerId() != room.getOwnerId()) return null;

        InteractionWiredExtra definition = sharedSource(saved.source(), reference);
        return definition != null ? new SourceStack(definition, saved.tile()) : null;
    }

    private static SourceStack liveStack(Room sourceRoom, WiredExtraVariableReference reference) {
        InteractionWiredExtra source = sourceDefinition(sourceRoom, reference);
        if (source == null) return null;

        return new SourceStack(
                source,
                WiredExecutionOrderUtil.sort(sourceRoom.getRoomSpecialTypes().getExtras(source.getX(), source.getY())));
    }

    /**
     * The user fx this room draws for its references, at most {@code limit}, each box once (over the first reference
     * that imports it).
     */
    public static List<ImportedFx> importedFx(Room room, int limit) {
        List<ImportedFx> result = new ArrayList<>();
        if (room == null || room.getRoomSpecialTypes() == null || limit <= 0) return result;

        Set<Integer> seen = new HashSet<>();
        Map<Integer, Map<Integer, Integer>> idsBySourceRoom = new HashMap<>();
        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (!(extra instanceof WiredExtraVariableReference reference)
                    || !reference.isUserReference()
                    || !imports(room, reference, VARIABLE_FX)) {
                continue;
            }

            SourceStack stack = sourceStack(room, reference);
            if (stack == null || shownUserVariable(stack) != stack.source()) continue;

            for (InteractionWiredExtra addon : sharedAddons(stack, VARIABLE_FX)) {
                if (!(addon instanceof WiredExtraVariableFx box) || !box.isUserFx() || !seen.add(box.getId())) {
                    continue;
                }

                Map<Integer, Integer> variableIds = idsBySourceRoom.computeIfAbsent(
                        reference.getSourceRoomId(), sourceRoomId -> referencedIds(room, sourceRoomId));
                result.add(new ImportedFx(box, reference, variableIds));
                if (result.size() >= limit) return result;
            }
        }
        return result;
    }

    /** The variable a user fx in the stack shows: the first user variable on the tile, as the fx box picks it. */
    private static InteractionWiredExtra shownUserVariable(SourceStack stack) {
        for (InteractionWiredExtra extra : stack.tile()) {
            if (extra instanceof WiredExtraUserVariable) return extra;
        }
        return null;
    }

    /**
     * The variables of {@code sourceRoomId} this room references, source id to reference id (the lowest reference
     * id when several point at one variable).
     */
    static Map<Integer, Integer> referencedIds(Room room, int sourceRoomId) {
        Map<Integer, Integer> ids = new HashMap<>();
        if (room == null || room.getRoomSpecialTypes() == null) return ids;

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableReference reference
                    && reference.getSourceRoomId() == sourceRoomId
                    && reference.getSourceVariableItemId() > 0) {
                ids.merge(reference.getSourceVariableItemId(), reference.getId(), Math::min);
            }
        }
        return ids;
    }

    /** Whether a reference in the room imports fx, so the room's fx service has to keep watching. */
    public static boolean importsFx(Room room) {
        if (room == null || room.getRoomSpecialTypes() == null) return false;

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableAddonImport box && box.allows(VARIABLE_FX)) return true;
        }
        return false;
    }

    /**
     * The Share box's Synchronize: every loaded room of the owner that references a variable on the box's tile (and
     * the box's own room) gets its fx re-sent and its variable lists pushed again, with the shared add-ons as they
     * are now. Returns the number of rooms refreshed.
     */
    public static int synchronize(Room room, WiredExtraVariableAddonShare share) {
        if (room == null || share == null || room.getRoomSpecialTypes() == null) return 0;

        Set<Integer> shared = new HashSet<>();
        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(share.getX(), share.getY())) {
            if ((extra instanceof WiredExtraUserVariable user && user.isSharedAvailability())
                    || (extra instanceof WiredExtraRoomVariable global && global.isSharedAvailability())) {
                shared.add(extra.getId());
            }
        }

        List<Room> targets = new ArrayList<>();
        targets.add(room);
        for (Room other : loadedRooms()) {
            if (targets.size() >= MAX_SYNCHRONIZED_ROOMS) break;
            if (other != room
                    && other.isLoaded()
                    && other.getOwnerId() == room.getOwnerId()
                    && references(other, room.getId(), shared)) {
                targets.add(other);
            }
        }

        for (Room target : targets) {
            runOnPool(() -> refresh(target));
        }
        return targets.size();
    }

    private static void refresh(Room room) {
        WiredVariableFxSupport.resync(room);
        if (room.getUserVariableManager() != null) room.getUserVariableManager().broadcastSnapshot();
        if (room.getRoomVariableManager() != null) room.getRoomVariableManager().broadcastSnapshot();
    }

    private static boolean references(Room room, int sourceRoomId, Set<Integer> sourceItemIds) {
        if (sourceItemIds.isEmpty() || room.getRoomSpecialTypes() == null) return false;

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableReference reference
                    && reference.getSourceRoomId() == sourceRoomId
                    && sourceItemIds.contains(reference.getSourceVariableItemId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBox(
            Room room,
            InteractionWiredExtra definition,
            Class<? extends WiredExtraVariableAddonSharing> type,
            int category) {
        if (room == null || definition == null || room.getRoomSpecialTypes() == null) return false;

        Collection<InteractionWiredExtra> extras =
                room.getRoomSpecialTypes().getExtras(definition.getX(), definition.getY());
        if (extras == null) return false;

        for (InteractionWiredExtra extra : extras) {
            if (type.isInstance(extra) && type.cast(extra).allows(category)) return true;
        }
        return false;
    }

    private static boolean inCategory(InteractionWiredExtra extra, int category) {
        return switch (category) {
            case TEXT_CONNECTORS -> extra instanceof WiredExtraVariableTextConnector;
            case SUBVARIABLE_CREATORS ->
                extra instanceof WiredExtraVariableLevelUpSystem || extra instanceof WiredExtraTimeUtilities;
            case VARIABLE_FX -> extra instanceof WiredExtraVariableFx;
            default -> false;
        };
    }

    private static Room loadedRoom(int roomId) {
        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return null;
        }

        Room room = WiredPlatform.gameEnvironment().getRoomManager().getRoom(roomId);
        return room != null && room.isLoaded() ? room : null;
    }

    private static List<Room> loadedRooms() {
        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return Collections.emptyList();
        }
        return WiredPlatform.gameEnvironment().getRoomManager().getActiveRooms();
    }

    private static void runOnPool(Runnable task) {
        if (WiredPlatform.threading() != null) {
            WiredPlatform.threading().run(task);
        } else {
            task.run();
        }
    }
}
