package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.messages.outgoing.wired.WiredUserVariablesDataComposer;
import com.eu.habbo.threading.ThreadPooling;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * What the user, furni and room variable managers share when a value changes: how the change is classified for the
 * wired triggers, and how the viewers allowed to inspect wired receive the variables of the room.
 */
final class RoomVariableChanges {
    private RoomVariableChanges() {}

    /** The kind of change a variable-changed trigger hears; NONE when the variable has no value or was not kept. */
    static WiredEvent.VariableChangeKind changeKind(
            boolean hasValue, boolean existedBefore, Integer previousValue, boolean existsAfter, Integer currentValue) {
        if (!hasValue || !existedBefore || !existsAfter) {
            return WiredEvent.VariableChangeKind.NONE;
        }

        if (Objects.equals(previousValue, currentValue)) {
            return WiredEvent.VariableChangeKind.UNCHANGED;
        }

        int previousNumericValue = (previousValue != null) ? previousValue : 0;
        int currentNumericValue = (currentValue != null) ? currentValue : 0;

        return (currentNumericValue > previousNumericValue)
                ? WiredEvent.VariableChangeKind.INCREASED
                : WiredEvent.VariableChangeKind.DECREASED;
    }

    /** As above, for a variable that exists before and after the change (a room variable always does). */
    static WiredEvent.VariableChangeKind changeKind(boolean hasValue, Integer previousValue, Integer currentValue) {
        return changeKind(hasValue, true, previousValue, true, currentValue);
    }

    /** Sends the user, furni and room variables of the room to one viewer, if that viewer may inspect wired. */
    static void sendSnapshot(Room room, Habbo habbo) {
        if (habbo == null || habbo.getClient() == null || !room.canInspectWired(habbo)) {
            return;
        }

        habbo.getClient()
                .sendResponse(new WiredUserVariablesDataComposer(
                        room.getUserVariableManager().createSnapshot(),
                        room.getFurniVariableManager().createSnapshot(),
                        room.getRoomVariableManager().createSnapshot()));
    }

    /** Sends the user, furni and room variables of the room to everyone in it who may inspect wired. */
    static void broadcastSnapshot(Room room) {
        RoomUserVariableManager.Snapshot userSnapshot =
                room.getUserVariableManager().createSnapshot();
        RoomFurniVariableManager.Snapshot furniSnapshot =
                room.getFurniVariableManager().createSnapshot();
        RoomVariableManager.Snapshot roomSnapshot =
                room.getRoomVariableManager().createSnapshot();

        for (Habbo habbo : room.getCurrentHabbos().values()) {
            if (habbo == null || habbo.getClient() == null || !room.canInspectWired(habbo)) {
                continue;
            }

            habbo.getClient()
                    .sendResponse(new WiredUserVariablesDataComposer(userSnapshot, furniSnapshot, roomSnapshot));
        }
    }

    /**
     * Runs {@code broadcast} 50 ms from now unless one is already waiting, so a burst of changes is sent once; it is
     * skipped when the room has unloaded in the meantime.
     */
    static void scheduleBroadcast(Room room, AtomicBoolean pending, ThreadPooling threading, Runnable broadcast) {
        if (pending.compareAndSet(false, true)) {
            threading.run(
                    () -> {
                        pending.set(false);
                        if (room.isLoaded()) {
                            broadcast.run();
                        }
                    },
                    50);
        }
    }
}
