package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.UserVariableHolders;
import com.eu.habbo.habbohotel.wired.WiredVariableChangeOrigin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tells the other loaded rooms that a shared variable changed, so their "variable changed" triggers hear it with the
 * official "another room" origin ({@code wiredfurni.params.variables.trigger_origin.1}). A shared user or room variable
 * lives in the room that owns it and is read elsewhere through a variable reference: a write in either place reaches
 * the owner room (on the variable itself) and every other room that references it (on its reference).
 *
 * <p>Only the change is passed on, never a write. What the other room's stacks write while they answer it stays in
 * that room, so two rooms that answer each other's changes cannot relay them back and forth for ever. The other rooms
 * are notified on the pool, outside the writing room's locks.
 */
public final class WiredSharedVariableChanges {
    /** Set while a relayed change runs its stacks, so their writes are not relayed again. */
    private static final ThreadLocal<Boolean> RELAYING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private WiredSharedVariableChanges() {}

    /** The shared variable a definition box stands for: the owner room and its variable box. */
    record SharedSource(int roomId, int variableItemId, boolean user) {}

    /** A room variable of {@code room} changed; passes the change on when the variable is shared. */
    public static void roomVariableChanged(
            Room room,
            InteractionWiredExtra definition,
            WiredEvent.VariableChangeKind changeKind,
            long previousValue,
            long currentValue) {
        SharedSource source = sourceOf(room, definition);
        if (source == null || source.user() || changeKind == WiredEvent.VariableChangeKind.NONE) return;

        for (Room other : otherRooms(room)) {
            runOnPool(() -> {
                for (int localId : localIds(other, source)) {
                    dispatch(WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, other)
                            .variableTargetType(3)
                            .variableDefinitionItemId(localId)
                            .variableChangeKind(changeKind)
                            .variableValues(previousValue, currentValue));
                }
            });
        }
    }

    /** A user variable of {@code room} changed; passes the change on when the variable is shared. */
    public static void userVariableChanged(
            Room room,
            InteractionWiredExtra definition,
            int userId,
            boolean created,
            boolean deleted,
            WiredEvent.VariableChangeKind changeKind,
            long previousValue,
            long currentValue) {
        SharedSource source = sourceOf(room, definition);
        if (source == null || !source.user() || userId <= 0) return;
        if (!created && !deleted && changeKind == WiredEvent.VariableChangeKind.NONE) return;

        for (Room other : otherRooms(room)) {
            runOnPool(() -> {
                // The user is heard where they are: a room they are not in has no holder to fire on.
                RoomUnit unit = UserVariableHolders.unitOf(other, userId);
                if (unit == null) return;
                for (int localId : localIds(other, source)) {
                    dispatch(WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, other)
                            .actor(unit)
                            .tile(unit.getCurrentLocation())
                            .variableTargetType(0)
                            .variableDefinitionItemId(localId)
                            .variableCreated(created)
                            .variableDeleted(deleted)
                            .variableChangeKind(changeKind)
                            .variableValues(previousValue, currentValue));
                }
            });
        }
    }

    /** The shared source of a definition box in {@code room}; null when it is not shared. */
    static SharedSource sourceOf(Room room, InteractionWiredExtra definition) {
        if (room == null || definition == null || RELAYING.get()) return null;

        if (definition instanceof WiredExtraVariableReference reference) {
            if (reference.getSourceRoomId() <= 0 || reference.getSourceVariableItemId() <= 0) return null;
            return new SharedSource(
                    reference.getSourceRoomId(), reference.getSourceVariableItemId(), reference.isUserReference());
        }
        if (definition instanceof WiredExtraUserVariable variable && variable.isSharedAvailability()) {
            return new SharedSource(room.getId(), variable.getId(), true);
        }
        if (definition instanceof WiredExtraRoomVariable variable && variable.isSharedAvailability()) {
            return new SharedSource(room.getId(), variable.getId(), false);
        }
        return null;
    }

    /** The boxes of {@code room} that stand for the shared variable: the variable itself or its references. */
    static List<Integer> localIds(Room room, SharedSource source) {
        if (room == null || room.getRoomSpecialTypes() == null) return Collections.emptyList();

        List<Integer> ids = new ArrayList<>();
        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraVariableReference reference) {
                if (reference.getSourceRoomId() == source.roomId()
                        && reference.getSourceVariableItemId() == source.variableItemId()
                        && reference.isUserReference() == source.user()) {
                    ids.add(reference.getId());
                }
            } else if (room.getId() == source.roomId() && extra.getId() == source.variableItemId()) {
                if (source.user() ? extra instanceof WiredExtraUserVariable : extra instanceof WiredExtraRoomVariable) {
                    ids.add(extra.getId());
                }
            }
        }
        return ids;
    }

    /**
     * Fires the change in the other room with the "another room" origin on the event only, so what its stacks write in
     * turn stays a wired write of that room (and is not relayed again).
     */
    private static void dispatch(WiredEvent.Builder event) {
        if (!WiredManager.isEnabled()) return;
        relaying(() -> WiredManager.handleEvent(
                event.variableChangeOrigin(WiredVariableChangeOrigin.OTHER_ROOM).build()));
    }

    /** Runs {@code body} as the stacks of a relayed change: nothing it writes is relayed again. */
    static void relaying(Runnable body) {
        RELAYING.set(Boolean.TRUE);
        try {
            body.run();
        } finally {
            RELAYING.remove();
        }
    }

    private static List<Room> otherRooms(Room room) {
        if (WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return Collections.emptyList();
        }

        List<Room> result = new ArrayList<>();
        for (Room other : WiredPlatform.gameEnvironment().getRoomManager().getActiveRooms()) {
            if (other != null && other != room && other.getId() != room.getId() && other.isLoaded()) {
                result.add(other);
            }
        }
        return result;
    }

    private static void runOnPool(Runnable task) {
        if (WiredPlatform.threading() != null) {
            WiredPlatform.threading().run(task);
        } else {
            task.run();
        }
    }
}
