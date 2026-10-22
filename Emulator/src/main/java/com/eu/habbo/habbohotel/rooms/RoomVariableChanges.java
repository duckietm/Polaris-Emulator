package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredVariableLevelSystemSupport;
import com.eu.habbo.messages.outgoing.wired.WiredUserVariablesDataComposer;
import com.eu.habbo.threading.ThreadPooling;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

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
     * skipped when the room has unloaded in the meantime. Without threading (the emulator is starting or stopping)
     * nothing is scheduled and nothing is marked as waiting, so the next request still schedules one.
     */
    static void scheduleBroadcast(Room room, AtomicBoolean pending, ThreadPooling threading, Runnable broadcast) {
        if (threading == null) {
            return;
        }

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

    /** Hears one variable change, as the holder's own trigger does. */
    interface ChangeListener {
        void changed(
                int definitionItemId,
                boolean hasValue,
                boolean existedBefore,
                Integer previousValue,
                boolean existsAfter,
                Integer currentValue);
    }

    /**
     * Reports how the derived parts of a user or furni variable (level-up and time utilities) changed with it. The
     * holder's stamps are read once, at the first part that resolves.
     */
    static void emitDerivedChanges(
            Room room,
            int targetType,
            InteractionWiredExtra definitionExtra,
            WiredVariableDefinitionInfo definitionInfo,
            boolean existedBefore,
            Integer previousValue,
            boolean existsAfter,
            Integer currentValue,
            Supplier<RoomVariableAssignment> holderStamps,
            ChangeListener listener) {
        RoomVariableAssignment stamps = null;
        boolean stampsRead = false;

        for (WiredVariableDefinitionInfo derivedDefinition : WiredVariableLevelSystemSupport.getDerivedDefinitions(
                room, targetType, definitionExtra, definitionInfo)) {
            WiredVariableLevelSystemSupport.DerivedDefinition resolvedDefinition =
                    WiredVariableLevelSystemSupport.resolveDerivedDefinition(
                            room, targetType, derivedDefinition.getItemId());

            if (resolvedDefinition == null) {
                continue;
            }

            if (!stampsRead) {
                stamps = holderStamps.get();
                stampsRead = true;
            }

            int createdAt = (stamps != null) ? stamps.getCreatedAt() : 0;
            int updatedAt = (stamps != null) ? stamps.getUpdatedAt() : 0;
            // A value change also moved the update time; a last-update-time part compares against the old one.
            int previousUpdatedAt = (stamps != null && !Objects.equals(previousValue, currentValue))
                    ? stamps.getPreviousUpdatedAt()
                    : updatedAt;
            Integer derivedPreviousValue = existedBefore
                    ? WiredVariableLevelSystemSupport.getDerivedValue(
                            room,
                            resolvedDefinition.getLevelSystem(),
                            resolvedDefinition.getSubvariableType(),
                            previousValue,
                            createdAt,
                            previousUpdatedAt)
                    : null;
            Integer derivedCurrentValue = existsAfter
                    ? WiredVariableLevelSystemSupport.getDerivedValue(
                            room,
                            resolvedDefinition.getLevelSystem(),
                            resolvedDefinition.getSubvariableType(),
                            currentValue,
                            createdAt,
                            updatedAt)
                    : null;

            // A presence part (is_maxed) is created when it appears and removed when it goes, by itself.
            boolean presence = WiredVariableLevelSystemSupport.isPresencePart(resolvedDefinition);
            listener.changed(
                    derivedDefinition.getItemId(),
                    true,
                    presence ? derivedPreviousValue != null : existedBefore,
                    derivedPreviousValue,
                    presence ? derivedCurrentValue != null : existsAfter,
                    derivedCurrentValue);
        }
    }
}
