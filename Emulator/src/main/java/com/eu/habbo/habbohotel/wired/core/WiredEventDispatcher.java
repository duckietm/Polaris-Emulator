package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboSaysKeyword;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Internal owner of WIRED event admission, stack iteration and event-level result semantics. */
final class WiredEventDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEventDispatcher.class);

    @FunctionalInterface
    interface StackProcessor {
        boolean process(WiredStack stack, WiredEvent event, long triggerTime, boolean negateConditions);
    }

    @FunctionalInterface
    interface DiagnosticSink {
        void log(Room room, String format, Object... arguments);
    }

    private final WiredExecutionGuard guard;
    private final WiredStackIndex eventIndex;
    private final WiredStackRepository repository;
    private final StackProcessor stackProcessor;
    private final DiagnosticSink diagnostics;

    WiredEventDispatcher(
            WiredExecutionGuard guard,
            WiredStackRepository repository,
            StackProcessor stackProcessor,
            DiagnosticSink diagnostics) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.eventIndex = repository.eventIndex();
        this.stackProcessor = Objects.requireNonNull(stackProcessor, "stackProcessor");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    boolean dispatch(WiredEvent event, boolean negateConditions) {
        if (event == null) {
            return false;
        }

        Room room = event.getRoom();
        if (room == null || !room.isLoaded()) {
            return false;
        }

        int roomId = room.getId();
        if (!this.guard.tryEnterDeferredPublication(
                roomId, room, event.getType(), WiredExecutionGuard.EntryKind.EVENT)) {
            return false;
        }

        boolean published = false;
        try {
            List<WiredStack> stacks = this.eventIndex.getStacks(room, event.getType());
            if (stacks.isEmpty()) {
                return false;
            }
            this.guard.publishDeferredAdmission(roomId);
            published = true;
            return process(event, room, stacks, negateConditions, 0);
        } finally {
            this.guard.exitDeferredAdmission(roomId, published);
        }
    }

    boolean dispatchForSourceItem(WiredEvent event, int sourceItemId) {
        if (event == null || sourceItemId <= 0) {
            return false;
        }

        Room room = event.getRoom();
        if (room == null || !room.isLoaded()) {
            return false;
        }

        int roomId = room.getId();
        if (!this.guard.tryEnterDeferredPublication(
                roomId, room, event.getType(), WiredExecutionGuard.EntryKind.SOURCE_ITEM)) {
            return false;
        }

        boolean published = false;
        try {
            List<WiredStack> stacks = this.repository.getStacksForSourceItem(room, event.getType(), sourceItemId);
            if (stacks.isEmpty()) {
                return false;
            }
            this.guard.publishDeferredAdmission(roomId);
            published = true;
            return process(event, room, stacks, false, sourceItemId);
        } finally {
            this.guard.exitDeferredAdmission(roomId, published);
        }
    }

    private boolean process(
            WiredEvent event, Room room, List<WiredStack> stacks, boolean negateConditions, int sourceItemId) {
        if (stacks.isEmpty()) {
            return false;
        }

        if (sourceItemId > 0) {
            this.diagnostics.log(
                    room,
                    "Processing {} stacks for event type {} from source item {}",
                    stacks.size(),
                    event.getType(),
                    sourceItemId);
        } else {
            this.diagnostics.log(room, "Processing {} stacks for event type {}", stacks.size(), event.getType());
        }

        boolean anyTriggered = false;
        boolean suppressSaysOutput = false;
        long triggerTime = event.getCreatedAtMs();

        for (WiredStack stack : stacks) {
            try {
                boolean triggered = this.stackProcessor.process(stack, event, triggerTime, negateConditions);
                if (!triggered) {
                    continue;
                }

                anyTriggered = true;
                if ((event.getType() == WiredEvent.Type.USER_SAYS)
                        && (stack.triggerItem() instanceof WiredTriggerHabboSaysKeyword keywordTrigger)
                        && keywordTrigger.isHideMessage()) {
                    suppressSaysOutput = true;
                }
            } catch (WiredLimitException exception) {
                this.diagnostics.log(room, "Stack execution stopped (limit): {}", exception.getMessage());
            } catch (Exception exception) {
                logStackFailure(room, sourceItemId, exception);
            }
        }

        return event.getType() == WiredEvent.Type.USER_SAYS ? suppressSaysOutput : anyTriggered;
    }

    private void logStackFailure(Room room, int sourceItemId, Exception exception) {
        if (sourceItemId > 0) {
            LOGGER.error(
                    "Error processing source wired stack in room {} for item {}: {}",
                    room.getId(),
                    sourceItemId,
                    exception.getMessage(),
                    exception);
            this.diagnostics.log(room, "Source stack error: {}", exception.getMessage());
            return;
        }

        LOGGER.error("Error processing wired stack in room {}: {}", room.getId(), exception.getMessage(), exception);
        this.diagnostics.log(room, "Stack error: {}", exception.getMessage());
    }
}
