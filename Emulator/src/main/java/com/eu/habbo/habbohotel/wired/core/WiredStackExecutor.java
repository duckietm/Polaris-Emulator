package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecutionLimit;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksUser;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboSaysKeyword;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackExecutedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackTriggeredEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/** Internal owner of one-stack WIRED orchestration and legacy plugin callbacks. */
final class WiredStackExecutor {

    interface Hooks {
        List<InteractionWiredEffect> executeSelectors(WiredStack stack, WiredContext context);

        void applySelectionFilterExtras(
                WiredStack stack, WiredContext context, List<InteractionWiredEffect> executedSelectors);

        boolean selectorsHaveRequiredTargets(List<InteractionWiredEffect> executedSelectors, WiredContext context);

        void finalizeSelectors(List<InteractionWiredEffect> executedSelectors, WiredContext context, long currentTime);

        void executeEffects(WiredStack stack, List<IWiredEffect> effects, WiredContext context, long currentTime);
    }

    @FunctionalInterface
    interface DiagnosticSink {
        void log(Room room, String format, Object... arguments);
    }

    private enum Mode {
        EVENT,
        DIRECT
    }

    private final WiredServices services;
    private final int maxStepsPerStack;
    private final WiredConditionEvaluator conditionEvaluator;
    private final WiredEffectPlanner effectPlanner;
    private final WiredExecutionGuard executionGuard;
    private final Hooks hooks;
    private final LongSupplier clock;
    private final DiagnosticSink diagnostics;
    private final WiredStructuredDiagnostics structuredDiagnostics;

    WiredStackExecutor(
            WiredServices services,
            int maxStepsPerStack,
            WiredConditionEvaluator conditionEvaluator,
            WiredEffectPlanner effectPlanner,
            WiredExecutionGuard executionGuard,
            Hooks hooks,
            LongSupplier clock,
            DiagnosticSink diagnostics,
            WiredStructuredDiagnostics structuredDiagnostics) {
        this.services = Objects.requireNonNull(services, "services");
        this.maxStepsPerStack = maxStepsPerStack;
        this.conditionEvaluator = Objects.requireNonNull(conditionEvaluator, "conditionEvaluator");
        this.effectPlanner = Objects.requireNonNull(effectPlanner, "effectPlanner");
        this.executionGuard = Objects.requireNonNull(executionGuard, "executionGuard");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.structuredDiagnostics = Objects.requireNonNull(structuredDiagnostics, "structuredDiagnostics");
    }

    boolean executeEvent(WiredStack stack, WiredEvent event, long currentTime, boolean negateConditions) {
        return execute(stack, event, currentTime, negateConditions, Mode.EVENT);
    }

    boolean executeDirect(WiredStack stack, WiredEvent event, boolean negateConditions) {
        if (stack == null || event == null || event.getRoom() == null) {
            return false;
        }
        return execute(stack, event, this.clock.getAsLong(), negateConditions, Mode.DIRECT);
    }

    private boolean execute(WiredStack stack, WiredEvent event, long currentTime, boolean negateConditions, Mode mode) {
        Room room = event.getRoom();
        WiredTextInputCaptureSupport.CaptureResult captureResult = null;
        if (mode == Mode.EVENT) {
            captureResult = resolveTextInputCapture(stack, event);
            if (!captureResult.matches()) {
                return false;
            }
        }

        if (stack.trigger().requiresActor() && event.getActor().isEmpty()) {
            return false;
        }
        if (!stackHasExecutableOutcome(stack, event)) {
            return false;
        }

        WiredState state = new WiredState(this.maxStepsPerStack);
        WiredContext context = new WiredContext(event, stack.triggerItem(), stack, this.services, state, null);
        if (captureResult != null) {
            WiredTextInputCaptureSupport.applyToContext(context, room, captureResult);
        }
        WiredRoomDiagnostics roomDiagnostics = this.executionGuard.diagnostics(room.getId());
        state.step();

        int stackCost = estimateStackCost(stack, this.executionGuard.currentChainDepth(room.getId()));
        String monitorSourceLabel = monitorSourceLabel(stack.triggerItem(), event);
        int monitorSourceId = monitorSourceId(stack.triggerItem());
        logMatchedStack(room, stack, event, negateConditions, mode);

        List<InteractionWiredEffect> executedSelectors = Collections.emptyList();
        if (stack.hasEffects()) {
            executedSelectors = this.hooks.executeSelectors(stack, context);
            this.hooks.applySelectionFilterExtras(stack, context, executedSelectors);
        }
        if (!this.hooks.selectorsHaveRequiredTargets(executedSelectors, context)) {
            return false;
        }

        boolean conditionsPassed = this.conditionEvaluator.outcomeForExecution(stack, context, negateConditions);
        List<IWiredEffect> executableEffects = this.effectPlanner.executableEffects(stack, conditionsPassed);
        boolean hasSpecialOutcome = conditionsPassed && hasSpecialTriggerOutcome(stack, event);
        if (!shouldContinueAfterConditionCheck(stack, room, conditionsPassed, executableEffects, hasSpecialOutcome)) {
            return false;
        }

        WiredExtraExecutionLimit executionLimitExtra = executionLimitExtra(room, stack);
        if (executionLimitExtra != null && !executionLimitExtra.tryAcquireExecutionSlot(currentTime)) {
            this.diagnostics.log(
                    room,
                    mode == Mode.DIRECT
                            ? "Execution limit blocked direct stack {} (max {} in {} ms)"
                            : "Execution limit blocked stack {} (max {} in {} ms)",
                    stack.triggerItem() != null ? stack.triggerItem().getId() : "null",
                    executionLimitExtra.getMaxExecutions(),
                    executionLimitExtra.getTimeWindowMs());
            return false;
        }

        if (!fireTriggeredEvent(stack, event)) {
            this.diagnostics.log(
                    room, mode == Mode.DIRECT ? "Direct stack cancelled by plugin" : "Stack cancelled by plugin");
            return false;
        }

        if (!roomDiagnostics.tryConsumeExecutionBudget(
                stackCost,
                currentTime,
                monitorSourceLabel,
                monitorSourceId,
                stackMonitorReason(stack, event, stackCost))) {
            this.diagnostics.log(
                    room,
                    mode == Mode.DIRECT ? "Execution cap blocked direct stack {}" : "Execution cap blocked stack {}",
                    stack.triggerItem() != null ? stack.triggerItem().getId() : "null");
            return false;
        }

        if (mode == Mode.EVENT) {
            applyClickUserOptions(stack, event, conditionsPassed);
        }

        RoomUnit actor = event.getActor().orElse(null);
        if (stack.triggerItem() instanceof InteractionWiredTrigger trigger) {
            trigger.activateBox(room, actor, currentTime);
        }
        activateExtras(room, stack.triggerItem(), actor, currentTime);
        this.hooks.finalizeSelectors(executedSelectors, context, currentTime);

        if (!executableEffects.isEmpty()) {
            this.hooks.executeEffects(stack, executableEffects, context, currentTime);
        }

        fireExecutedEvent(stack, event);
        long elapsedMs = state.elapsedMs();
        roomDiagnostics.recordExecution(
                elapsedMs,
                this.clock.getAsLong(),
                monitorSourceLabel,
                monitorSourceId,
                executionMonitorReason(stack, elapsedMs));
        this.structuredDiagnostics.execution(
                room.getId(),
                monitorSourceId,
                event.getType(),
                executableEffects.size(),
                elapsedMs,
                WiredStructuredDiagnostics.Outcome.EXECUTED);
        return true;
    }

    private void logMatchedStack(Room room, WiredStack stack, WiredEvent event, boolean negateConditions, Mode mode) {
        if (mode == Mode.DIRECT) {
            this.diagnostics.log(
                    room,
                    "Direct stack execution for item {} (conditions: {}, effects: {}, negated: {})",
                    stack.triggerItem() != null ? stack.triggerItem().getId() : "null",
                    stack.conditions().size(),
                    stack.effects().size(),
                    negateConditions);
            return;
        }

        this.diagnostics.log(
                room,
                "Trigger matched: {} at item {} (conditions: {}, effects: {})",
                event.getType(),
                stack.triggerItem() != null ? stack.triggerItem().getId() : "null",
                stack.conditions().size(),
                stack.effects().size());
    }

    private boolean shouldContinueAfterConditionCheck(
            WiredStack stack,
            Room room,
            boolean conditionsPassed,
            List<IWiredEffect> executableEffects,
            boolean hasSpecialOutcome) {
        if (stack.hasConditions()) {
            this.diagnostics.log(
                    room, "Evaluating {} conditions...", stack.conditions().size());
            if (!conditionsPassed && !executableEffects.isEmpty()) {
                this.diagnostics.log(room, "Conditions failed, executing negative effects");
                return true;
            }
            if (!conditionsPassed) {
                this.diagnostics.log(room, "Conditions failed, aborting stack");
                return false;
            }
            if (hasSpecialOutcome || !executableEffects.isEmpty()) {
                return true;
            }
            this.diagnostics.log(room, "Conditions passed, but no executable effects remain");
            return false;
        }

        if (!conditionsPassed) {
            this.diagnostics.log(room, "No conditions in stack, negated execution aborted");
            return false;
        }
        if (hasSpecialOutcome || !executableEffects.isEmpty()) {
            this.diagnostics.log(room, "No conditions in stack, proceeding to effects");
            return true;
        }
        this.diagnostics.log(room, "No conditions in stack, but no executable effects remain");
        return false;
    }

    private static WiredTextInputCaptureSupport.CaptureResult resolveTextInputCapture(
            WiredStack stack, WiredEvent event) {
        if (stack == null || event == null) {
            return WiredTextInputCaptureSupport.CaptureResult.noMatch();
        }
        if (event.getType() != WiredEvent.Type.USER_SAYS
                || !(stack.triggerItem() instanceof WiredTriggerHabboSaysKeyword)) {
            return stack.trigger().matches(stack.triggerItem(), event)
                    ? WiredTextInputCaptureSupport.CaptureResult.matched(new LinkedHashMap<>())
                    : WiredTextInputCaptureSupport.CaptureResult.noMatch();
        }
        return WiredTextInputCaptureSupport.resolve(stack, event);
    }

    private static boolean stackHasExecutableOutcome(WiredStack stack, WiredEvent event) {
        if (stack == null) {
            return false;
        }
        if (stack.hasEffects()) {
            return true;
        }
        return hasSpecialTriggerOutcome(stack, event);
    }

    private static boolean hasSpecialTriggerOutcome(WiredStack stack, WiredEvent event) {
        if (stack == null) {
            return false;
        }
        if (stack.triggerItem() instanceof WiredTriggerHabboSaysKeyword keywordTrigger) {
            return keywordTrigger.isHideMessage();
        }
        if (event != null
                && event.getType() == WiredEvent.Type.USER_CLICKS_USER
                && stack.triggerItem() instanceof WiredTriggerHabboClicksUser clickTrigger) {
            return clickTrigger.isBlockMenuOpen() || clickTrigger.isDoNotRotate();
        }
        return false;
    }

    private static void applyClickUserOptions(WiredStack stack, WiredEvent event, boolean conditionsPassed) {
        if (conditionsPassed
                && event.getType() == WiredEvent.Type.USER_CLICKS_USER
                && stack.triggerItem() instanceof WiredTriggerHabboClicksUser clickTrigger
                && event.getActor().isPresent()) {
            WiredTriggerHabboClicksUser.applyRuntimeOptions(
                    event.getActor().get(), clickTrigger.isBlockMenuOpen(), clickTrigger.isDoNotRotate());
        }
    }

    private static void activateExtras(Room room, HabboItem triggerItem, RoomUnit roomUnit, long currentTime) {
        if (triggerItem == null || room.getRoomSpecialTypes() == null) {
            return;
        }
        Collection<InteractionWiredExtra> extras =
                room.getRoomSpecialTypes().getExtras(triggerItem.getX(), triggerItem.getY());
        if (extras != null) {
            for (InteractionWiredExtra extra : extras) {
                extra.activateBox(room, roomUnit, currentTime);
            }
        }
    }

    private static WiredExtraExecutionLimit executionLimitExtra(Room room, WiredStack stack) {
        InteractionWiredExtra extra = stackExtra(room, stack, WiredExtraExecutionLimit.class);
        return extra instanceof WiredExtraExecutionLimit limit ? limit : null;
    }

    private static <T extends InteractionWiredExtra> InteractionWiredExtra stackExtra(
            Room room, WiredStack stack, Class<T> extraClass) {
        if (room == null || stack == null || stack.triggerItem() == null || room.getRoomSpecialTypes() == null) {
            return null;
        }
        Collection<InteractionWiredExtra> extras = room.getRoomSpecialTypes()
                .getExtras(stack.triggerItem().getX(), stack.triggerItem().getY());
        if (extras == null || extras.isEmpty()) {
            return null;
        }
        for (InteractionWiredExtra extra : extras) {
            if (extraClass.isInstance(extra)) {
                return extra;
            }
        }
        return null;
    }

    private static boolean fireTriggeredEvent(WiredStack stack, WiredEvent event) {
        if (!(stack.triggerItem() instanceof InteractionWiredTrigger trigger)) {
            return true;
        }
        WiredStackTriggeredEvent pluginEvent = new WiredStackTriggeredEvent(
                event.getRoom(), event.getActor().orElse(null), trigger, legacyEffects(stack), legacyConditions(stack));
        return !WiredPlatform.pluginManager().fireEvent(pluginEvent).isCancelled();
    }

    private static void fireExecutedEvent(WiredStack stack, WiredEvent event) {
        if (stack.triggerItem() instanceof InteractionWiredTrigger trigger) {
            WiredPlatform.pluginManager()
                    .fireEvent(new WiredStackExecutedEvent(
                            event.getRoom(),
                            event.getActor().orElse(null),
                            trigger,
                            legacyEffects(stack),
                            legacyConditions(stack)));
        }
    }

    private static Set<InteractionWiredEffect> legacyEffects(WiredStack stack) {
        Set<InteractionWiredEffect> legacyEffects = new HashSet<>();
        for (IWiredEffect effect : stack.effects()) {
            if (effect instanceof InteractionWiredEffect wiredEffect) {
                legacyEffects.add(wiredEffect);
            }
        }
        return legacyEffects;
    }

    private static Set<InteractionWiredCondition> legacyConditions(WiredStack stack) {
        Set<InteractionWiredCondition> legacyConditions = new HashSet<>();
        for (IWiredCondition condition : stack.conditions()) {
            if (condition instanceof InteractionWiredCondition wiredCondition) {
                legacyConditions.add(wiredCondition);
            }
        }
        return legacyConditions;
    }

    private static int estimateStackCost(WiredStack stack, int recursionDepth) {
        int cost = 1 + Math.max(0, stack.conditions().size());
        for (IWiredEffect effect : stack.effects()) {
            if (effect == null) {
                continue;
            }
            cost += effect.isSelector() ? 2 : 3;
            if (effect.getDelay() > 0) {
                cost += 4;
            }
        }
        return Math.max(1, cost + Math.max(0, recursionDepth) * 2);
    }

    private static String monitorSourceLabel(HabboItem triggerItem, WiredEvent event) {
        if (triggerItem != null
                && triggerItem.getBaseItem() != null
                && triggerItem.getBaseItem().getInteractionType() != null) {
            return triggerItem.getBaseItem().getInteractionType().getName();
        }
        return event != null && event.getType() != null ? event.getType().name() : "room";
    }

    private static int monitorSourceId(HabboItem triggerItem) {
        return triggerItem != null ? triggerItem.getId() : 0;
    }

    private static String stackMonitorReason(WiredStack stack, WiredEvent event, int stackCost) {
        int selectors = 0;
        int delayedEffects = 0;
        for (IWiredEffect effect : stack.effects()) {
            if (effect == null) {
                continue;
            }
            if (effect.isSelector()) {
                selectors++;
            }
            if (effect.getDelay() > 0) {
                delayedEffects++;
            }
        }
        return String.format(
                "Trigger %s with %d condition(s), %d effect(s), %d selector(s), %d delayed effect(s) and estimated cost %d",
                event.getType().name(),
                stack.conditions().size(),
                stack.effects().size(),
                selectors,
                delayedEffects,
                stackCost);
    }

    private static String executionMonitorReason(WiredStack stack, long elapsedMs) {
        return String.format(
                "Stack with %d condition(s) and %d effect(s) completed in %dms",
                stack.conditions().size(), stack.effects().size(), elapsedMs);
    }
}
