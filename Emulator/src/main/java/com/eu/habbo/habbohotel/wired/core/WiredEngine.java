package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterFurniByVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterUser;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFilterUsersByVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecutionLimit;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraOrEval;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRandom;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUnseen;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksUser;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboSaysKeyword;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemStateComposer;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackExecutedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackTriggeredEvent;
import gnu.trove.map.hash.THashMap;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The central engine for processing wired events.
 * <p>
 * This is the single entry point for all wired execution in the new architecture.
 * It receives {@link WiredEvent} objects, finds matching stacks via {@link WiredStackIndex},
 * evaluates conditions, and executes effects.
 * </p>
 *
 * <h3>Execution Flow:</h3>
 * <ol>
 *   <li>Receive event via {@link #handleEvent(WiredEvent)}</li>
 *   <li>Find candidate stacks for the event type</li>
 *   <li>For each stack, check if trigger matches</li>
 *   <li>Evaluate all conditions (respecting AND/OR mode)</li>
 *   <li>Execute effects (respecting random/unseen modifiers)</li>
 *   <li>Handle delays for timed effects</li>
 * </ol>
 *
 * <h3>Safety Features:</h3>
 * <ul>
 *   <li>Step limits via {@link WiredState} prevent infinite loops</li>
 *   <li>Effect cooldowns prevent rapid re-triggering</li>
 *   <li>Exceptions are caught and logged, not propagated</li>
 * </ul>
 *
 * @see WiredEvent
 * @see WiredContext
 * @see WiredStackIndex
 */
public final class WiredEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredEngine.class);

    /** Maximum recursion depth to prevent infinite loops (e.g., collision + chase) */
    public static int MAX_RECURSION_DEPTH = 10;

    /** Maximum events of same type per room within rate limit window before banning */
    public static int MAX_EVENTS_PER_WINDOW = 100;

    /** Time window for counting rapid events (milliseconds) */
    public static long RATE_LIMIT_WINDOW_MS = 10000;

    /** Duration to ban wired execution in a room after abuse detected (milliseconds) */
    public static long WIRED_BAN_DURATION_MS = 600000;

    /** Monitor usage window in milliseconds */
    public static int MONITOR_USAGE_WINDOW_MS = 1000;

    /** Monitor execution cap per room window */
    public static int MONITOR_USAGE_LIMIT = 50000;

    /** Maximum delayed events allowed per room at the same time */
    public static int MONITOR_DELAYED_EVENTS_LIMIT = 50000;

    /** Average execution threshold that marks overload */
    public static int MONITOR_OVERLOAD_AVERAGE_MS = 50;

    /** Peak execution threshold that marks overload */
    public static int MONITOR_OVERLOAD_PEAK_MS = 150;

    /** Consecutive overloaded windows required before recording overload */
    public static int MONITOR_OVERLOAD_CONSECUTIVE_WINDOWS = 2;

    /** Usage percentage threshold that marks a room as heavy */
    public static int MONITOR_HEAVY_USAGE_PERCENT = 70;

    /** Consecutive windows above threshold before marking heavy */
    public static int MONITOR_HEAVY_CONSECUTIVE_WINDOWS = 5;

    /** Delayed queue percentage threshold that contributes to heavy state */
    public static int MONITOR_HEAVY_DELAYED_PERCENT = 60;

    private final WiredServices services;
    private final WiredStackIndex index;
    private final int maxStepsPerStack;

    /** Track unseen effect indices per room+tile for round-robin selection */
    private final ConcurrentHashMap<String, Integer> unseenIndices;

    /** Track recursion depth per room to prevent infinite loops */
    private final ConcurrentHashMap<Integer, Integer> roomRecursionDepth;

    /** Track event timestamps per room+eventType for rate limiting: key = "roomId:eventType" */
    private final ConcurrentHashMap<String, EventRateTracker> eventRateLimiters;

    /** Track rooms that are banned from wired execution: roomId -> ban expiry timestamp */
    private final ConcurrentHashMap<Integer, Long> bannedRooms;

    /** Track monitor diagnostics per room */
    private final ConcurrentHashMap<Integer, WiredRoomDiagnostics> roomDiagnostics;

    /** Cache room+eventType+sourceItemId -> matching stacks for source-triggered timer events */
    private final ConcurrentHashMap<String, List<WiredStack>> sourceStacksByTriggerKey;

    /** Track filter-selector animation tokens so rapid executions do not reset newer animations */
    private final ConcurrentHashMap<Integer, Long> filteredSelectorAnimationTokens;

    /**
     * Create a new wired engine.
     *
     * @param services the services for performing side effects
     * @param index the stack index for finding matching stacks
     * @param maxStepsPerStack maximum steps per stack execution (loop protection)
     */
    public WiredEngine(WiredServices services, WiredStackIndex index, int maxStepsPerStack) {
        if (services == null) throw new IllegalArgumentException("Services cannot be null");
        if (index == null) throw new IllegalArgumentException("Index cannot be null");
        if (maxStepsPerStack <= 0) throw new IllegalArgumentException("Max steps must be positive");

        this.services = services;
        this.index = index;
        this.maxStepsPerStack = maxStepsPerStack;
        this.unseenIndices = new ConcurrentHashMap<>();
        this.roomRecursionDepth = new ConcurrentHashMap<>();
        this.eventRateLimiters = new ConcurrentHashMap<>();
        this.bannedRooms = new ConcurrentHashMap<>();
        this.roomDiagnostics = new ConcurrentHashMap<>();
        this.sourceStacksByTriggerKey = new ConcurrentHashMap<>();
        this.filteredSelectorAnimationTokens = new ConcurrentHashMap<>();
    }

    /**
     * Handle a wired event by finding and executing matching stacks.
     *
     * @param event the event to handle
     * @return true if any stack was triggered (useful for SAY_SOMETHING to suppress message)
     */
    public boolean handleEvent(WiredEvent event) {
        return handleEvent(event, false);
    }

    public boolean handleEvent(WiredEvent event, boolean negateConditions) {
        if (event == null) {
            return false;
        }

        Room room = event.getRoom();
        if (room == null || !room.isLoaded()) {
            return false;
        }

        int roomId = room.getId();

        if (this.isRoomBanned(roomId)) {
            return false;
        }

        // Soft rate limiting to prevent rapid-fire event spam without banning whole rooms
        if (isRateLimited(roomId, room, event.getType())) {
            return false;
        }

        // Check and increment recursion depth to prevent infinite loops
        int currentDepth = roomRecursionDepth.merge(roomId, 1, Integer::sum);
        if (currentDepth > MAX_RECURSION_DEPTH) {
            roomRecursionDepth.merge(roomId, -1, Integer::sum);
            getDiagnostics(roomId).recordRecursionTimeout(
                    System.currentTimeMillis(),
                    String.format("Recursion depth %d/%d while handling %s", currentDepth, MAX_RECURSION_DEPTH, event.getType().name()),
                    event.getType().name(),
                    0
            );
            LOGGER.warn("Wired recursion limit reached in room {} (depth: {}). " +
                    "Possible infinite loop detected (e.g., collision + chase). Aborting.", roomId, currentDepth);
            debug(room, "RECURSION LIMIT REACHED - aborting to prevent crash");
            return false;
        }

        try {
            return handleEventInternal(event, room, negateConditions);
        } finally {
            // Decrement recursion depth
            roomRecursionDepth.compute(roomId, (k, v) -> (v == null || v <= 1) ? null : v - 1);
        }
    }

    /**
     * Handle a wired event when the source trigger item is already known.
     * This is mainly used by timed wired triggers to avoid scanning unrelated stacks.
     *
     * @param event the event to handle
     * @param sourceItemId the trigger item id that originated the event
     * @return true if any matching stack was triggered
     */
    public boolean handleEventForSourceItem(WiredEvent event, int sourceItemId) {
        if (event == null || sourceItemId <= 0) {
            return false;
        }

        Room room = event.getRoom();
        if (room == null || !room.isLoaded()) {
            return false;
        }

        int roomId = room.getId();

        if (this.isRoomBanned(roomId)) {
            return false;
        }

        if (isRateLimited(roomId, room, event.getType())) {
            return false;
        }

        int currentDepth = roomRecursionDepth.merge(roomId, 1, Integer::sum);
        if (currentDepth > MAX_RECURSION_DEPTH) {
            roomRecursionDepth.merge(roomId, -1, Integer::sum);
            LOGGER.warn("Wired recursion limit reached in room {} (depth: {}). " +
                    "Possible infinite loop detected (source item execution). Aborting.", roomId, currentDepth);
            debug(room, "RECURSION LIMIT REACHED - aborting source-item execution");
            return false;
        }

        try {
            return handleEventForSourceItemInternal(event, room, sourceItemId);
        } finally {
            roomRecursionDepth.compute(roomId, (k, v) -> (v == null || v <= 1) ? null : v - 1);
        }
    }

    /**
     * Internal event handling optimized for a known source trigger item.
     */
    private boolean handleEventForSourceItemInternal(WiredEvent event, Room room, int sourceItemId) {
        List<WiredStack> stacks = getStacksForSourceItem(room, event.getType(), sourceItemId);
        if (stacks.isEmpty()) {
            return false;
        }

        debug(room, "Processing {} stacks for event type {} from source item {}", stacks.size(), event.getType(), sourceItemId);

        boolean anyTriggered = false;
        boolean suppressSaysOutput = false;
        long triggerTime = event.getCreatedAtMs();

        for (WiredStack stack : stacks) {
            try {
                boolean triggered = processStack(stack, event, triggerTime);
                if (triggered) {
                    anyTriggered = true;

                    if ((event.getType() == WiredEvent.Type.USER_SAYS)
                            && (stack.triggerItem() instanceof WiredTriggerHabboSaysKeyword)
                            && ((WiredTriggerHabboSaysKeyword) stack.triggerItem()).isHideMessage()) {
                        suppressSaysOutput = true;
                    }
                }
            } catch (WiredLimitException limitEx) {
                debug(room, "Stack execution stopped (limit): {}", limitEx.getMessage());
            } catch (Exception ex) {
                LOGGER.error("Error processing source wired stack in room {} for item {}: {}",
                        room.getId(), sourceItemId, ex.getMessage(), ex);
                debug(room, "Source stack error: {}", ex.getMessage());
            }
        }

        if (event.getType() == WiredEvent.Type.USER_SAYS) {
            return suppressSaysOutput;
        }

        return anyTriggered;
    }

    /**
     * Find all stacks for a specific room/event/source item combination.
     * Multiple stacks can legally share the same trigger item.
     */
    private List<WiredStack> getStacksForSourceItem(Room room, WiredEvent.Type eventType, int sourceItemId) {
        String cacheKey = room.getId() + ":" + eventType.name() + ":" + sourceItemId;

        List<WiredStack> cached = sourceStacksByTriggerKey.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<WiredStack> allStacks = index.getStacks(room, eventType);
        if (allStacks.isEmpty()) {
            sourceStacksByTriggerKey.put(cacheKey, Collections.emptyList());
            return Collections.emptyList();
        }

        List<WiredStack> matching = new ArrayList<>();
        for (WiredStack stack : allStacks) {
            if (stack == null || stack.triggerItem() == null) {
                continue;
            }

            if (stack.triggerItem().getId() == sourceItemId) {
                matching.add(stack);
            }
        }

        List<WiredStack> result = matching.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(matching);
        sourceStacksByTriggerKey.put(cacheKey, result);
        return result;
    }

    /**
     * Internal event handling after recursion check.
     */
    private boolean handleEventInternal(WiredEvent event, Room room, boolean negateConditions) {

        // Find candidate stacks for this event type
        List<WiredStack> stacks = index.getStacks(room, event.getType());
        if (stacks.isEmpty()) {
            return false;
        }

        debug(room, "Processing {} stacks for event type {}", stacks.size(), event.getType());

        boolean anyTriggered = false;
        boolean suppressSaysOutput = false;
        long triggerTime = event.getCreatedAtMs();

        for (WiredStack stack : stacks) {
            try {
                boolean triggered = processStack(stack, event, triggerTime, negateConditions);
                if (triggered) {
                    anyTriggered = true;

                    if ((event.getType() == WiredEvent.Type.USER_SAYS)
                            && (stack.triggerItem() instanceof WiredTriggerHabboSaysKeyword)
                            && ((WiredTriggerHabboSaysKeyword) stack.triggerItem()).isHideMessage()) {
                        suppressSaysOutput = true;
                    }
                }
            } catch (WiredLimitException limitEx) {
                debug(room, "Stack execution stopped (limit): {}", limitEx.getMessage());
            } catch (Exception ex) {
                LOGGER.error("Error processing wired stack in room {}: {}", room.getId(), ex.getMessage(), ex);
                debug(room, "Stack error: {}", ex.getMessage());
            }
        }

        if (event.getType() == WiredEvent.Type.USER_SAYS) {
            return suppressSaysOutput;
        }

        return anyTriggered;
    }

    /**
     * Process a single wired stack.
     */
    private boolean processStack(WiredStack stack, WiredEvent event, long currentTime) {
        return processStack(stack, event, currentTime, false);
    }

    private boolean processStack(WiredStack stack, WiredEvent event, long currentTime, boolean negateConditions) {
        Room room = event.getRoom();
        WiredTextInputCaptureSupport.CaptureResult captureResult = resolveTextInputCapture(stack, event);

        // Check if trigger matches
        if (!captureResult.matches()) {
            return false;
        }

        // Check if trigger requires actor
        if (stack.trigger().requiresActor() && !event.getActor().isPresent()) {
            return false;
        }

        if (!stackHasExecutableOutcome(stack, event)) {
            return false;
        }

        // Create execution context with stack reference
        WiredState state = new WiredState(maxStepsPerStack);
        WiredContext ctx = new WiredContext(event, stack.triggerItem(), stack, services, state, null);
        WiredTextInputCaptureSupport.applyToContext(ctx, room, captureResult);
        WiredRoomDiagnostics diagnostics = getDiagnostics(room.getId());

        // Initial step for trigger
        state.step();

        int stackCost = estimateStackCost(stack, roomRecursionDepth.getOrDefault(room.getId(), 0));
        String monitorSourceLabel = getMonitorSourceLabel(stack.triggerItem(), event);
        int monitorSourceId = getMonitorSourceId(stack.triggerItem());

        debug(room, "Trigger matched: {} at item {} (conditions: {}, effects: {})",
              event.getType(),
              stack.triggerItem() != null ? stack.triggerItem().getId() : "null",
              stack.conditions().size(),
              stack.effects().size());

        // Run selectors before conditions so targets are available
        List<InteractionWiredEffect> executedSelectors = Collections.emptyList();
        if (stack.hasEffects()) {
            executedSelectors = executeSelectors(stack, ctx);
            applySelectionFilterExtras(stack, ctx, executedSelectors);
        }

        if (!selectorsHaveRequiredTargets(executedSelectors, ctx)) {
            return false;
        }

        boolean conditionsPassedForExecution = getConditionOutcomeForExecution(stack, ctx, negateConditions);
        List<IWiredEffect> executableEffects = getExecutableEffectsForCurrentExecution(stack, conditionsPassedForExecution);
        boolean hasSpecialOutcome = conditionsPassedForExecution && hasSpecialTriggerOutcome(stack, event);

        if (!shouldContinueAfterConditionCheck(stack, room, conditionsPassedForExecution, executableEffects, hasSpecialOutcome)) {
            return false;
        }

        WiredExtraExecutionLimit executionLimitExtra = getExecutionLimitExtra(room, stack);
        if (executionLimitExtra != null && !executionLimitExtra.tryAcquireExecutionSlot(currentTime)) {
            debug(room, "Execution limit blocked stack {} (max {} in {} ms)",
                    stack.triggerItem() != null ? stack.triggerItem().getId() : "null",
                    executionLimitExtra.getMaxExecutions(),
                    executionLimitExtra.getTimeWindowMs());
            return false;
        }

        // Fire plugin event (WiredStackTriggeredEvent)
        if (!fireTriggeredEvent(stack, event)) {
            debug(room, "Stack cancelled by plugin");
            return false;
        }

        if (!diagnostics.tryConsumeExecutionBudget(
                stackCost,
                currentTime,
                monitorSourceLabel,
                monitorSourceId,
                buildStackMonitorReason(stack, event, stackCost))) {
            debug(room, "Execution cap blocked stack {}", stack.triggerItem() != null ? stack.triggerItem().getId() : "null");
            return false;
        }

        if (conditionsPassedForExecution
                && (event.getType() == WiredEvent.Type.USER_CLICKS_USER)
                && (stack.triggerItem() instanceof WiredTriggerHabboClicksUser)
                && event.getActor().isPresent()) {
            WiredTriggerHabboClicksUser clickUserTrigger = (WiredTriggerHabboClicksUser) stack.triggerItem();
            WiredTriggerHabboClicksUser.applyRuntimeOptions(
                    event.getActor().get(),
                    clickUserTrigger.isBlockMenuOpen(),
                    clickUserTrigger.isDoNotRotate());
        }

        RoomUnit actor = event.getActor().orElse(null);

        // Only show the trigger/selector activation when the stack is actually allowed to continue.
        if (stack.triggerItem() instanceof InteractionWiredTrigger) {
            InteractionWiredTrigger trigger = (InteractionWiredTrigger) stack.triggerItem();
            trigger.activateBox(room, actor, currentTime);
        }

        activateExtras(room, stack.triggerItem(), actor, currentTime);
        finalizeSelectors(executedSelectors, ctx, currentTime);

        // Execute effects
        if (!executableEffects.isEmpty()) {
            executeEffects(stack, executableEffects, ctx, currentTime);
        }

        // Fire executed event
        fireExecutedEvent(stack, event);
        diagnostics.recordExecution(
                state.elapsedMs(),
                System.currentTimeMillis(),
                monitorSourceLabel,
                monitorSourceId,
                buildExecutionMonitorReason(stack, state.elapsedMs())
        );

        return true;
    }

    public boolean executeDirectStack(WiredStack stack, WiredEvent event, boolean negateConditions) {
        if (stack == null || event == null) {
            return false;
        }

        Room room = event.getRoom();
        if (room == null) {
            return false;
        }

        if (stack.trigger().requiresActor() && !event.getActor().isPresent()) {
            return false;
        }

        if (!stackHasExecutableOutcome(stack, event)) {
            return false;
        }

        long currentTime = System.currentTimeMillis();

        WiredState state = new WiredState(maxStepsPerStack);
        WiredContext ctx = new WiredContext(event, stack.triggerItem(), stack, services, state, null);
        WiredRoomDiagnostics diagnostics = getDiagnostics(room.getId());

        state.step();

        int stackCost = estimateStackCost(stack, roomRecursionDepth.getOrDefault(room.getId(), 0));
        String monitorSourceLabel = getMonitorSourceLabel(stack.triggerItem(), event);
        int monitorSourceId = getMonitorSourceId(stack.triggerItem());

        debug(room, "Direct stack execution for item {} (conditions: {}, effects: {}, negated: {})",
                stack.triggerItem() != null ? stack.triggerItem().getId() : "null",
                stack.conditions().size(),
                stack.effects().size(),
                negateConditions);

        List<InteractionWiredEffect> executedSelectors = Collections.emptyList();
        if (stack.hasEffects()) {
            executedSelectors = executeSelectors(stack, ctx);
            applySelectionFilterExtras(stack, ctx, executedSelectors);
        }

        if (!selectorsHaveRequiredTargets(executedSelectors, ctx)) {
            return false;
        }

        boolean conditionsPassedForExecution = getConditionOutcomeForExecution(stack, ctx, negateConditions);
        List<IWiredEffect> executableEffects = getExecutableEffectsForCurrentExecution(stack, conditionsPassedForExecution);
        boolean hasSpecialOutcome = conditionsPassedForExecution && hasSpecialTriggerOutcome(stack, event);

        if (!shouldContinueAfterConditionCheck(stack, room, conditionsPassedForExecution, executableEffects, hasSpecialOutcome)) {
            return false;
        }

        WiredExtraExecutionLimit executionLimitExtra = getExecutionLimitExtra(room, stack);
        if (executionLimitExtra != null && !executionLimitExtra.tryAcquireExecutionSlot(currentTime)) {
            debug(room, "Execution limit blocked direct stack {} (max {} in {} ms)",
                    stack.triggerItem() != null ? stack.triggerItem().getId() : "null",
                    executionLimitExtra.getMaxExecutions(),
                    executionLimitExtra.getTimeWindowMs());
            return false;
        }

        if (!fireTriggeredEvent(stack, event)) {
            debug(room, "Direct stack cancelled by plugin");
            return false;
        }

        if (!diagnostics.tryConsumeExecutionBudget(
                stackCost,
                currentTime,
                monitorSourceLabel,
                monitorSourceId,
                buildStackMonitorReason(stack, event, stackCost))) {
            debug(room, "Execution cap blocked direct stack {}", stack.triggerItem() != null ? stack.triggerItem().getId() : "null");
            return false;
        }

        RoomUnit actor = event.getActor().orElse(null);

        if (stack.triggerItem() instanceof InteractionWiredTrigger) {
            InteractionWiredTrigger trigger = (InteractionWiredTrigger) stack.triggerItem();
            trigger.activateBox(room, actor, currentTime);
        }

        activateExtras(room, stack.triggerItem(), actor, currentTime);
        finalizeSelectors(executedSelectors, ctx, currentTime);

        if (!executableEffects.isEmpty()) {
            executeEffects(stack, executableEffects, ctx, currentTime);
        }

        fireExecutedEvent(stack, event);
        diagnostics.recordExecution(
                state.elapsedMs(),
                System.currentTimeMillis(),
                monitorSourceLabel,
                monitorSourceId,
                buildExecutionMonitorReason(stack, state.elapsedMs())
        );

        return true;
    }

    public boolean shouldExecuteDirectStack(WiredStack stack, WiredEvent event, boolean negateConditions) {
        if (stack == null || event == null) {
            return false;
        }

        Room room = event.getRoom();
        if (room == null) {
            return false;
        }

        if (stack.trigger().requiresActor() && !event.getActor().isPresent()) {
            return false;
        }

        if (!stack.hasEffects()) {
            return false;
        }

        WiredState state = new WiredState(maxStepsPerStack);
        WiredContext ctx = new WiredContext(event, stack.triggerItem(), stack, services, state, null);
        state.step();

        List<InteractionWiredEffect> executedSelectors = Collections.emptyList();
        if (stack.hasEffects()) {
            executedSelectors = executeSelectors(stack, ctx);
            applySelectionFilterExtras(stack, ctx, executedSelectors);
        }

        if (!selectorsHaveRequiredTargets(executedSelectors, ctx)) {
            return false;
        }

        boolean conditionsPassedForExecution = getConditionOutcomeForExecution(stack, ctx, negateConditions);
        List<IWiredEffect> executableEffects = getExecutableEffectsForCurrentExecution(stack, conditionsPassedForExecution);
        return !executableEffects.isEmpty();
    }

    private boolean wouldTriggerStack(WiredStack stack, WiredEvent event, long currentTime) {
        Room room = event.getRoom();
        WiredTextInputCaptureSupport.CaptureResult captureResult = resolveTextInputCapture(stack, event);

        if (!captureResult.matches()) {
            return false;
        }

        if (stack.trigger().requiresActor() && !event.getActor().isPresent()) {
            return false;
        }

        if (!stackHasExecutableOutcome(stack, event)) {
            return false;
        }

        WiredState state = new WiredState(maxStepsPerStack);
        WiredContext ctx = new WiredContext(event, stack.triggerItem(), stack, services, state, null);
        WiredTextInputCaptureSupport.applyToContext(ctx, room, captureResult);

        state.step();

        List<InteractionWiredEffect> executedSelectors = Collections.emptyList();
        if (stack.hasEffects()) {
            executedSelectors = executeSelectors(stack, ctx);
            applySelectionFilterExtras(stack, ctx, executedSelectors);
        }

        if (!selectorsHaveRequiredTargets(executedSelectors, ctx)) {
            return false;
        }

        boolean conditionsPassedForExecution = getConditionOutcomeForExecution(stack, ctx, false);
        if (!conditionsPassedForExecution) {
            return false;
        }

        List<IWiredEffect> executableEffects = getExecutableEffectsForCurrentExecution(stack, true);
        boolean hasSpecialOutcome = hasSpecialTriggerOutcome(stack, event);
        if (executableEffects.isEmpty() && !hasSpecialOutcome) {
            return false;
        }

        WiredExtraExecutionLimit executionLimitExtra = getExecutionLimitExtra(room, stack);
        return executionLimitExtra == null || executionLimitExtra.canExecuteAt(currentTime);
    }

    private boolean stackHasExecutableOutcome(WiredStack stack, WiredEvent event) {
        if (stack == null) {
            return false;
        }

        if (stack.hasEffects()) {
            return true;
        }

        if (stack.triggerItem() instanceof WiredTriggerHabboSaysKeyword) {
            return ((WiredTriggerHabboSaysKeyword) stack.triggerItem()).isHideMessage();
        }

        if ((event != null)
                && (event.getType() == WiredEvent.Type.USER_CLICKS_USER)
                && (stack.triggerItem() instanceof WiredTriggerHabboClicksUser)) {
            WiredTriggerHabboClicksUser trigger = (WiredTriggerHabboClicksUser) stack.triggerItem();
            return trigger.isBlockMenuOpen() || trigger.isDoNotRotate();
        }

        return false;
    }

    private boolean hasSpecialTriggerOutcome(WiredStack stack, WiredEvent event) {
        if (stack == null) {
            return false;
        }

        if (stack.triggerItem() instanceof WiredTriggerHabboSaysKeyword) {
            return ((WiredTriggerHabboSaysKeyword) stack.triggerItem()).isHideMessage();
        }

        if ((event != null)
                && (event.getType() == WiredEvent.Type.USER_CLICKS_USER)
                && (stack.triggerItem() instanceof WiredTriggerHabboClicksUser)) {
            WiredTriggerHabboClicksUser trigger = (WiredTriggerHabboClicksUser) stack.triggerItem();
            return trigger.isBlockMenuOpen() || trigger.isDoNotRotate();
        }

        return false;
    }

    private boolean getConditionOutcomeForExecution(WiredStack stack, WiredContext ctx, boolean negateConditions) {
        if (!stack.hasConditions()) {
            return !negateConditions;
        }

        return shouldConditionsPass(stack, ctx, negateConditions);
    }

    private List<IWiredEffect> getExecutableEffectsForCurrentExecution(WiredStack stack, boolean conditionsPassed) {
        List<IWiredEffect> executableEffects = new ArrayList<>();

        for (IWiredEffect effect : stack.effects()) {
            if (effect == null || effect.isSelector()) {
                continue;
            }

            boolean negativeEffect = isNegativeConditionEffect(effect);

            if (conditionsPassed) {
                if (!negativeEffect) {
                    executableEffects.add(effect);
                }
                continue;
            }

            if (stack.hasConditions() && negativeEffect) {
                executableEffects.add(effect);
            }
        }

        return executableEffects;
    }

    private boolean isNegativeConditionEffect(IWiredEffect effect) {
        if (!(effect instanceof InteractionWiredEffect)) {
            return false;
        }

        WiredEffectType effectType = ((InteractionWiredEffect) effect).getType();
        return effectType == WiredEffectType.NEG_CALL_STACKS || effectType == WiredEffectType.NEG_SEND_SIGNAL;
    }

    private WiredTextInputCaptureSupport.CaptureResult resolveTextInputCapture(WiredStack stack, WiredEvent event) {
        if (stack == null || event == null) {
            return WiredTextInputCaptureSupport.CaptureResult.noMatch();
        }

        if (event.getType() != WiredEvent.Type.USER_SAYS || !(stack.triggerItem() instanceof WiredTriggerHabboSaysKeyword)) {
            return stack.trigger().matches(stack.triggerItem(), event)
                    ? WiredTextInputCaptureSupport.CaptureResult.matched(new LinkedHashMap<>())
                    : WiredTextInputCaptureSupport.CaptureResult.noMatch();
        }

        return WiredTextInputCaptureSupport.resolve(stack, event);
    }

    /**
     * Evaluate all conditions in a stack.
     */
    private boolean evaluateConditions(WiredStack stack, WiredContext ctx) {
        List<IWiredCondition> conditions = stack.conditions();

        return evaluateConditionsByMode(conditions, ctx, stack.conditionEvaluationMode(), stack.conditionEvaluationValue());
    }

    private boolean shouldContinueAfterConditionCheck(WiredStack stack, Room room, boolean conditionsPassedForExecution, List<IWiredEffect> executableEffects, boolean hasSpecialOutcome) {
        if (stack.hasConditions()) {
            debug(room, "Evaluating {} conditions...", stack.conditions().size());

            if (!conditionsPassedForExecution && !executableEffects.isEmpty()) {
                debug(room, "Conditions failed, executing negative effects");
                return true;
            }

            if (!conditionsPassedForExecution) {
                debug(room, "Conditions failed, aborting stack");
                return false;
            }

            if (hasSpecialOutcome || !executableEffects.isEmpty()) {
                return true;
            }

            debug(room, "Conditions passed, but no executable effects remain");
            return false;
        }

        if (!conditionsPassedForExecution) {
            debug(room, "No conditions in stack, negated execution aborted");
            return false;
        }

        if (hasSpecialOutcome || !executableEffects.isEmpty()) {
            debug(room, "No conditions in stack, proceeding to effects");
            return true;
        }

        debug(room, "No conditions in stack, but no executable effects remain");
        return false;
    }

    private boolean shouldConditionsPass(WiredStack stack, WiredContext ctx, boolean negateConditions) {
        boolean conditionsPassed = evaluateConditions(stack, ctx);
        debug(ctx.room(), "Conditions result: {}", conditionsPassed ? "PASSED" : "FAILED");
        return negateConditions ? !conditionsPassed : conditionsPassed;
    }

    /**
     * Evaluate conditions according to the configured stack mode.
     */
    private boolean evaluateConditionsByMode(List<IWiredCondition> conditions, WiredContext ctx, int evaluationMode, int evaluationValue) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        Room room = ctx.room();
        Map<String, List<Boolean>> groupedOrResults = new LinkedHashMap<>();
        int matchedRequirements = 0;
        int totalRequirements = 0;

        for (IWiredCondition condition : conditions) {
            ctx.state().step();

            boolean result = condition.evaluate(ctx);
            String conditionKey = getConditionGroupKey(condition);

            if (condition.operator() == WiredConditionOperator.OR) {
                groupedOrResults.computeIfAbsent(conditionKey, ignored -> new ArrayList<>()).add(result);
                debug(room, "  Condition (OR group {}) {}: {}", conditionKey, condition.getClass().getSimpleName(), result ? "PASS" : "FAIL");
                continue;
            }

            totalRequirements++;

            if (result) {
                matchedRequirements++;
            }

            debug(room, "  Condition {}: {}", condition.getClass().getSimpleName(), result ? "PASS" : "FAIL");
        }

        for (Map.Entry<String, List<Boolean>> entry : groupedOrResults.entrySet()) {
            totalRequirements++;

            boolean groupPassed = entry.getValue().stream().anyMatch(Boolean::booleanValue);
            if (groupPassed) {
                matchedRequirements++;
            }

            debug(room, "  Condition (OR result {}) : {}", entry.getKey(), groupPassed ? "PASS" : "FAIL");
        }

        boolean matches = WiredExtraOrEval.matchesMode(evaluationMode, matchedRequirements, totalRequirements, evaluationValue);

        debug(room, "Condition eval mode {} value {} matched {}/{} logical requirements => {}", evaluationMode, evaluationValue, matchedRequirements, totalRequirements, matches ? "PASS" : "FAIL");
        return matches;
    }

    private String getConditionGroupKey(IWiredCondition condition) {
        if (condition instanceof InteractionWiredCondition) {
            return String.valueOf(((InteractionWiredCondition) condition).getType());
        }

        return condition.getClass().getName();
    }

    /**
     * Execute effects in a stack.
     */
    private void executeEffects(WiredStack stack, List<IWiredEffect> effects, WiredContext ctx, long currentTime) {
        if (effects.isEmpty()) {
            return;
        }

        // Determine which (regular) effects to execute
        List<IWiredEffect> toExecute;

        if (stack.useRandom()) {
            WiredExtraRandom randomExtra = getRandomExtra(ctx.room(), stack);
            if (effects.isEmpty()) {
                toExecute = new ArrayList<>();
            } else if (randomExtra != null) {
                toExecute = randomExtra.selectWiredEffects(effects);
                debug(ctx.room(), "Random mode: selected {} effect(s), skip window {}", toExecute.size(), randomExtra.getSkipExecutions());
            } else {
                int randomIndex = new Random().nextInt(effects.size());
                toExecute = Collections.singletonList(effects.get(randomIndex));
                debug(ctx.room(), "Random mode: selected effect {}/{}", randomIndex + 1, effects.size());
            }
        } else if (stack.useUnseen()) {
            // Unseen mode: execute in stable order with memory
            if (effects.isEmpty()) {
                toExecute = new ArrayList<>();
            } else {
                WiredExtraUnseen unseenExtra = getUnseenExtra(ctx.room(), stack);

                if (unseenExtra != null) {
                    toExecute = unseenExtra.selectWiredEffects(effects);

                    if (!toExecute.isEmpty()) {
                        int selectedIndex = effects.indexOf(toExecute.get(0));
                        debug(ctx.room(), "Unseen mode: selected effect {}/{}", selectedIndex + 1, effects.size());
                    } else {
                        debug(ctx.room(), "Unseen mode: no eligible effect found");
                    }
                } else {
                    int index = getNextUnseenIndex(stack, effects.size());
                    toExecute = Collections.singletonList(effects.get(index));
                    debug(ctx.room(), "Unseen mode fallback: selected effect {}/{}", index + 1, effects.size());
                }
            }
        } else if (stack.executeInOrder()) {
            debug(ctx.room(), "Ordered mode: executing effect batches in stack order by delay");
            executeOrderedEffects(effects, ctx, currentTime);
            return;
        } else {
            // Normal mode: preserve the physical stack order.
            // This matches the legacy handler behavior and avoids visual/state races
            // for combinations such as Move/Rotate + Match To Snapshot in the same stack.
            toExecute = new ArrayList<>(effects);
        }

        WiredMoveCarryHelper.beginMovementCollection();

        try (WiredInternalVariableSupport.UserMoveBatchScope ignored = WiredInternalVariableSupport.beginUserMoveBatch()) {
            // Execute selected effects
            for (int effectIndex = 0; effectIndex < toExecute.size(); effectIndex++) {
                IWiredEffect effect = toExecute.get(effectIndex);

                // Check if effect requires actor
                if (effect.requiresActor() && !ctx.hasActor()) {
                    continue;
                }

                // Handle delay
                int delay = effect.getDelay();
                if (delay > 0) {
                    List<IWiredEffect> delayedBatch = new ArrayList<>();
                    delayedBatch.add(effect);

                    while ((effectIndex + 1) < toExecute.size()) {
                        IWiredEffect nextEffect = toExecute.get(effectIndex + 1);

                        if (nextEffect == null || nextEffect.getDelay() != delay) {
                            break;
                        }

                        if (nextEffect.requiresActor() && !ctx.hasActor()) {
                            effectIndex++;
                            continue;
                        }

                        delayedBatch.add(nextEffect);
                        effectIndex++;
                    }

                    if (delayedBatch.size() == 1) {
                        scheduleDelayedEffect(effect, ctx, delay, currentTime);
                    } else {
                        scheduleOrderedEffectBatch(delayedBatch, ctx, delay, currentTime);
                    }
                } else {
                    // Execute immediately
                    ctx.state().step();
                    try {
                        effect.execute(ctx);

                        // Activate box animation after execution
                        if (effect instanceof InteractionWiredEffect) {
                            InteractionWiredEffect wiredEffect = (InteractionWiredEffect) effect;
                            wiredEffect.setCooldown(currentTime);
                            wiredEffect.activateBox(ctx.room(), ctx.actor().orElse(null), currentTime);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Error executing effect: {}", e.getMessage());
                    }
                }
            }
        } finally {
            ServerMessage movementComposer = WiredMoveCarryHelper.finishMovementCollection();
            if (movementComposer != null) {
                ctx.room().sendComposer(movementComposer);
            }
        }
    }

    /**
     * Execute selector effects before conditions so ctx.targets() is populated.
     */
    private List<InteractionWiredEffect> executeSelectors(WiredStack stack, WiredContext ctx) {
        List<IWiredEffect> effects = stack.effects();
        if (effects.isEmpty()) return Collections.emptyList();

        List<InteractionWiredEffect> executedSelectors = new ArrayList<>();
        List<IWiredEffect> immediateSelectors = new ArrayList<>();
        List<IWiredEffect> deferredSelectors = new ArrayList<>();

        for (IWiredEffect effect : effects) {
            if (!effect.isSelector()) continue;

            if (effect.usesExistingSelectorTargets()) {
                deferredSelectors.add(effect);
            } else {
                immediateSelectors.add(effect);
            }
        }

        executeSelectorList(immediateSelectors, ctx, executedSelectors);
        executeSelectorList(deferredSelectors, ctx, executedSelectors);

        return executedSelectors;
    }

    private void executeSelectorList(List<IWiredEffect> selectors, WiredContext ctx, List<InteractionWiredEffect> executedSelectors) {
        for (IWiredEffect effect : selectors) {
            if (effect.requiresActor() && !ctx.hasActor()) {
                continue;
            }

            ctx.state().step();
            try {
                effect.execute(ctx);
                if (effect instanceof InteractionWiredEffect) {
                    InteractionWiredEffect wiredEffect = (InteractionWiredEffect) effect;
                    executedSelectors.add(wiredEffect);

                    if (wiredEffect.usesExistingSelectorTargets()) {
                        setFilteredSelectorState(ctx.room(), wiredEffect, "3");
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Error executing selector: {}", e.getMessage());
            }
        }
    }

    private void finalizeSelectors(List<InteractionWiredEffect> executedSelectors, WiredContext ctx, long currentTime) {
        if (executedSelectors == null || executedSelectors.isEmpty()) {
            return;
        }

        Room room = ctx.room();
        RoomUnit actor = ctx.actor().orElse(null);

        for (InteractionWiredEffect wiredEffect : executedSelectors) {
            wiredEffect.setCooldown(currentTime);

            if (wiredEffect.usesExistingSelectorTargets()) {
                animateFilteredSelectorBox(room, wiredEffect);
            } else {
                wiredEffect.activateBox(room, actor, currentTime);
            }
        }
    }

    private void animateFilteredSelectorBox(Room room, InteractionWiredEffect wiredEffect) {
        if (room == null || wiredEffect == null) {
            return;
        }

        // If wired is hidden, skip animation but ensure any stale token is cleaned up
        if (room.isHideWired()) {
            this.filteredSelectorAnimationTokens.remove(wiredEffect.getId());
            return;
        }

        long animationToken = System.nanoTime();
        this.filteredSelectorAnimationTokens.put(wiredEffect.getId(), animationToken);

        setFilteredSelectorState(room, wiredEffect, "3", animationToken, false);
        scheduleFilteredSelectorState(room, wiredEffect, "4", animationToken, 80L, false);
        scheduleFilteredSelectorState(room, wiredEffect, "5", animationToken, 160L, false);
        scheduleFilteredSelectorState(room, wiredEffect, "3", animationToken, 240L, true);
    }

    private void scheduleFilteredSelectorState(Room room, InteractionWiredEffect wiredEffect, String state, long animationToken, long delay, boolean clearToken) {
        Emulator.getThreading().run(() -> setFilteredSelectorState(room, wiredEffect, state, animationToken, clearToken), delay);
    }

    private void setFilteredSelectorState(Room room, InteractionWiredEffect wiredEffect, String state) {
        setFilteredSelectorState(room, wiredEffect, state, 0L, false);
    }

    private void setFilteredSelectorState(Room room, InteractionWiredEffect wiredEffect, String state, long animationToken, boolean clearToken) {
        if (room == null || wiredEffect == null || room.isHideWired()) {
            return;
        }

        if (animationToken != 0L) {
            Long currentToken = this.filteredSelectorAnimationTokens.get(wiredEffect.getId());
            if (currentToken == null || currentToken != animationToken) {
                return;
            }
        }

        if (!state.equals(wiredEffect.getExtradata())) {
            wiredEffect.setExtradata(state);
            room.sendComposer(new ItemStateComposer(wiredEffect).compose());
        }

        if (clearToken) {
            this.filteredSelectorAnimationTokens.remove(wiredEffect.getId(), animationToken);
        }
    }

    private void applySelectionFilterExtras(WiredStack stack, WiredContext ctx, List<InteractionWiredEffect> executedSelectors) {
        if (executedSelectors == null || executedSelectors.isEmpty()) {
            return;
        }

        Room room = ctx.room();
        if (room == null || stack.triggerItem() == null || room.getRoomSpecialTypes() == null) {
            return;
        }

        WiredSelectionFilterSupport.applySelectorFilters(room, stack.triggerItem(), ctx);
    }

    private boolean selectorsHaveRequiredTargets(List<InteractionWiredEffect> executedSelectors, WiredContext ctx) {
        if (executedSelectors == null || executedSelectors.isEmpty()) {
            return true;
        }

        for (InteractionWiredEffect selector : executedSelectors) {
            if (!selector.hasRequiredSelectorTargets(ctx)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Schedule a delayed effect execution.
     */
    private void scheduleDelayedEffect(IWiredEffect effect, WiredContext ctx, int delay, long triggerTime) {
        WiredRoomDiagnostics diagnostics = getDiagnostics(ctx.room().getId());
        String sourceLabel = getMonitorSourceLabel(ctx.triggerItem(), ctx.event());
        int sourceId = getMonitorSourceId(ctx.triggerItem());

        if (!diagnostics.tryScheduleDelayedEvent(
                System.currentTimeMillis(),
                sourceLabel,
                sourceId,
                String.format("Scheduling delayed effect %s with delay %d tick(s)", effect.getClass().getSimpleName(), delay))) {
            debug(ctx.room(), "Delayed events cap blocked effect {}", effect.getClass().getSimpleName());
            return;
        }

        // Delay is in 500ms ticks
        long delayMs = delay * 500L;
        long elapsedSinceTrigger = Math.max(0L, System.currentTimeMillis() - triggerTime);
        long remainingDelayMs = Math.max(0L, delayMs - elapsedSinceTrigger);
        Room room = ctx.room();
        RoomUnit actor = ctx.actor().orElse(null);

        Emulator.getThreading().run(() -> {
            if (!room.isLoaded() || room.getHabbos().isEmpty()) {
                diagnostics.completeDelayedEvent();
                return;
            }

            try {
                effect.execute(ctx);

                // Activate box animation after execution
                if (effect instanceof InteractionWiredEffect) {
                    InteractionWiredEffect wiredEffect = (InteractionWiredEffect) effect;
                    wiredEffect.setCooldown(System.currentTimeMillis());
                    wiredEffect.activateBox(room, actor, System.currentTimeMillis());
                }
            } catch (Exception e) {
                LOGGER.warn("Error executing delayed effect: {}", e.getMessage());
            } finally {
                diagnostics.completeDelayedEvent();
            }
        }, remainingDelayMs);
    }

    private void executeOrderedEffects(List<IWiredEffect> effects, WiredContext ctx, long currentTime) {
        if (effects == null || effects.isEmpty()) {
            return;
        }

        Map<Integer, List<IWiredEffect>> effectsByDelay = new LinkedHashMap<>();

        for (IWiredEffect effect : effects) {
            if (effect == null) {
                continue;
            }

            if (effect.requiresActor() && !ctx.hasActor()) {
                continue;
            }

            effectsByDelay.computeIfAbsent(effect.getDelay(), key -> new ArrayList<>()).add(effect);
        }

        for (Map.Entry<Integer, List<IWiredEffect>> entry : effectsByDelay.entrySet()) {
            int delay = entry.getKey();
            List<IWiredEffect> batch = entry.getValue();

            if (batch.isEmpty()) {
                continue;
            }

            if (delay > 0) {
                scheduleOrderedEffectBatch(batch, ctx, delay, currentTime);
            } else {
                executeOrderedEffectBatch(batch, ctx, currentTime, false);
            }
        }
    }

    /**
     * Preview whether a USER_SAYS event should suppress the public room chat output.
     * This mirrors trigger and condition eligibility without executing regular effects.
     */
    public boolean shouldSuppressUserSaysOutput(WiredEvent event) {
        if (event == null || event.getType() != WiredEvent.Type.USER_SAYS) {
            return false;
        }

        Room room = event.getRoom();
        if (room == null || !room.isLoaded()) {
            return false;
        }

        List<WiredStack> stacks = index.getStacks(room, event.getType());
        if (stacks.isEmpty()) {
            return false;
        }

        long triggerTime = event.getCreatedAtMs();

        for (WiredStack stack : stacks) {
            if (!(stack.triggerItem() instanceof WiredTriggerHabboSaysKeyword)) {
                continue;
            }

            WiredTriggerHabboSaysKeyword trigger = (WiredTriggerHabboSaysKeyword) stack.triggerItem();
            if (!trigger.isHideMessage()) {
                continue;
            }

            try {
                if (wouldTriggerStack(stack, event, triggerTime)) {
                    return true;
                }
            } catch (WiredLimitException limitEx) {
                debug(room, "Suppression preview stopped (limit): {}", limitEx.getMessage());
            } catch (Exception ex) {
                LOGGER.warn("Error previewing USER_SAYS suppression in room {}: {}", room.getId(), ex.getMessage());
            }
        }

        return false;
    }

    private void scheduleOrderedEffectBatch(List<IWiredEffect> batch, WiredContext ctx, int delay, long triggerTime) {
        WiredRoomDiagnostics diagnostics = getDiagnostics(ctx.room().getId());
        String sourceLabel = getMonitorSourceLabel(ctx.triggerItem(), ctx.event());
        int sourceId = getMonitorSourceId(ctx.triggerItem());

        if (!diagnostics.tryScheduleDelayedEvent(
                System.currentTimeMillis(),
                sourceLabel,
                sourceId,
                String.format("Scheduling ordered batch with %d effect(s) and delay %d tick(s)", batch.size(), delay))) {
            debug(ctx.room(), "Delayed events cap blocked ordered batch with {} effect(s)", batch.size());
            return;
        }

        long delayMs = delay * 500L;
        long elapsedSinceTrigger = Math.max(0L, System.currentTimeMillis() - triggerTime);
        long remainingDelayMs = Math.max(0L, delayMs - elapsedSinceTrigger);
        Room room = ctx.room();

        Emulator.getThreading().run(() -> {
            if (!room.isLoaded() || room.getHabbos().isEmpty()) {
                diagnostics.completeDelayedEvent();
                return;
            }

            try {
                executeOrderedEffectBatch(batch, ctx, System.currentTimeMillis(), true);
            } finally {
                diagnostics.completeDelayedEvent();
            }
        }, remainingDelayMs);
    }

    private void executeOrderedEffectBatch(List<IWiredEffect> batch, WiredContext ctx, long executionTime, boolean useExecutionTimeForCooldown) {
        Room room = ctx.room();
        RoomUnit actor = ctx.actor().orElse(null);

        WiredMoveCarryHelper.beginMovementCollection();

        try (WiredInternalVariableSupport.UserMoveBatchScope ignored = WiredInternalVariableSupport.beginUserMoveBatch()) {
            for (IWiredEffect effect : batch) {
                try {
                    if (!useExecutionTimeForCooldown) {
                        ctx.state().step();
                    }

                    effect.execute(ctx);

                    if (effect instanceof InteractionWiredEffect) {
                        InteractionWiredEffect wiredEffect = (InteractionWiredEffect) effect;
                        wiredEffect.setCooldown(executionTime);
                        wiredEffect.activateBox(room, actor, executionTime);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Error executing ordered effect batch item: {}", e.getMessage());
                }
            }
        } finally {
            ServerMessage movementComposer = WiredMoveCarryHelper.finishMovementCollection();
            if (movementComposer != null) {
                room.sendComposer(movementComposer);
            }
        }
    }

    /**
     * Get the next unseen index for round-robin selection.
     */
    private int getNextUnseenIndex(WiredStack stack, int effectCount) {
        String key = stack.triggerItem() != null
                ? String.valueOf(stack.triggerItem().getId())
                : "default";

        return unseenIndices.compute(key, (k, current) -> {
            if (current == null) current = -1;
            return (current + 1) % effectCount;
        });
    }

    /**
     * Fire the WiredStackTriggeredEvent for plugin compatibility.
     */
    private boolean fireTriggeredEvent(WiredStack stack, WiredEvent event) {
        // Build legacy collections for event
        if (stack.triggerItem() instanceof InteractionWiredTrigger) {
            // This event is checked for cancellation
            THashSet<InteractionWiredEffect> legacyEffects = new THashSet<>();
            THashSet<InteractionWiredCondition> legacyConditions = new THashSet<>();

            // Extract effects (all effects should now implement both interfaces)
            for (IWiredEffect eff : stack.effects()) {
                if (eff instanceof InteractionWiredEffect) {
                    legacyEffects.add((InteractionWiredEffect) eff);
                }
            }
            for (IWiredCondition cond : stack.conditions()) {
                if (cond instanceof InteractionWiredCondition) {
                    legacyConditions.add((InteractionWiredCondition) cond);
                }
            }

            WiredStackTriggeredEvent triggeredEvent = new WiredStackTriggeredEvent(
                    event.getRoom(),
                    event.getActor().orElse(null),
                    (InteractionWiredTrigger) stack.triggerItem(),
                    legacyEffects,
                    legacyConditions
            );

            return !Emulator.getPluginManager().fireEvent(triggeredEvent).isCancelled();
        }
        return true;
    }

    /**
     * Fire the WiredStackExecutedEvent for plugin compatibility.
     */
    private void fireExecutedEvent(WiredStack stack, WiredEvent event) {
        if (stack.triggerItem() instanceof InteractionWiredTrigger) {
            THashSet<InteractionWiredEffect> legacyEffects = new THashSet<>();
            THashSet<InteractionWiredCondition> legacyConditions = new THashSet<>();

            for (IWiredEffect eff : stack.effects()) {
                if (eff instanceof InteractionWiredEffect) {
                    legacyEffects.add((InteractionWiredEffect) eff);
                }
            }
            for (IWiredCondition cond : stack.conditions()) {
                if (cond instanceof InteractionWiredCondition) {
                    legacyConditions.add((InteractionWiredCondition) cond);
                }
            }

            Emulator.getPluginManager().fireEvent(new WiredStackExecutedEvent(
                    event.getRoom(),
                    event.getActor().orElse(null),
                    (InteractionWiredTrigger) stack.triggerItem(),
                    legacyEffects,
                    legacyConditions
            ));
        }
    }

    /**
     * Log a debug message if debug mode is enabled.
     */
    private void debug(Room room, String format, Object... args) {
        if (!WiredManager.isDebugEnabled()) {
            return;
        }

        if (!LOGGER.isDebugEnabled()) {
            return;
        }

        String message = String.format(format.replace("{}", "%s"), args);
        LOGGER.debug("[WiredEngine][Room {}] {}", room.getId(), message);
    }

    /**
     * Activate all extras at the trigger item's location for their animation.
     */
    private void activateExtras(Room room, HabboItem triggerItem, RoomUnit roomUnit, long millis) {
        if (triggerItem == null || room.getRoomSpecialTypes() == null) {
            return;
        }

        THashSet<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(
                triggerItem.getX(), triggerItem.getY());

        if (extras != null) {
            for (InteractionWiredExtra extra : extras) {
                extra.activateBox(room, roomUnit, millis);
            }
        }
    }

    private WiredExtraRandom getRandomExtra(Room room, WiredStack stack) {
        InteractionWiredExtra extra = getStackExtra(room, stack, WiredExtraRandom.class);

        return (extra instanceof WiredExtraRandom) ? (WiredExtraRandom) extra : null;
    }

    private WiredExtraUnseen getUnseenExtra(Room room, WiredStack stack) {
        InteractionWiredExtra extra = getStackExtra(room, stack, WiredExtraUnseen.class);

        return (extra instanceof WiredExtraUnseen) ? (WiredExtraUnseen) extra : null;
    }

    private WiredExtraExecutionLimit getExecutionLimitExtra(Room room, WiredStack stack) {
        InteractionWiredExtra extra = getStackExtra(room, stack, WiredExtraExecutionLimit.class);

        return (extra instanceof WiredExtraExecutionLimit) ? (WiredExtraExecutionLimit) extra : null;
    }

    private <T extends InteractionWiredExtra> InteractionWiredExtra getStackExtra(Room room, WiredStack stack, Class<T> extraClass) {
        if (room == null || stack == null || stack.triggerItem() == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        THashSet<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(
                stack.triggerItem().getX(),
                stack.triggerItem().getY());

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

    /**
     * Get the services used by this engine.
     * @return the wired services
     */
    public WiredServices getServices() {
        return services;
    }

    /**
     * Get the stack index used by this engine.
     * @return the stack index
     */
    public WiredStackIndex getIndex() {
        return index;
    }

    /**
     * Get the maximum steps per stack.
     * @return max steps
     */
    public int getMaxStepsPerStack() {
        return maxStepsPerStack;
    }

    /**
     * Clear all cached unseen indices.
     */
    public void clearUnseenCache() {
        unseenIndices.clear();
    }

    /**
     * Clear recursion tracking for a specific room.
     * Should be called when a room is unloaded.
     * @param roomId the room ID
     */
    public void clearRoomRecursionDepth(int roomId) {
        roomRecursionDepth.remove(roomId);
    }

    /**
     * Clear all recursion tracking.
     */
    public void clearAllRecursionDepth() {
        roomRecursionDepth.clear();
    }

    /**
     * Get the current recursion depth for a room (for debugging).
     * @param roomId the room ID
     * @return the current recursion depth, or 0 if not tracked
     */
    public int getRecursionDepth(int roomId) {
        return roomRecursionDepth.getOrDefault(roomId, 0);
    }

    /**
     * Clear rate limiters for a specific room.
     * Should be called when a room is unloaded.
     * @param roomId the room ID
     */
    public void clearRoomRateLimiters(int roomId) {
        String prefix = roomId + ":";
        eventRateLimiters.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Clear monitor diagnostics for a specific room.
     * @param roomId the room ID
     */
    public void clearRoomDiagnostics(int roomId) {
        roomDiagnostics.remove(roomId);
    }

    /**
     * Clear all monitor diagnostics.
     */
    public void clearAllDiagnostics() {
        roomDiagnostics.clear();
    }

    public void clearRoomDiagnosticsLogs(int roomId) {
        WiredRoomDiagnostics diagnostics = roomDiagnostics.get(roomId);

        if (diagnostics != null) {
            diagnostics.clearLogs();
        }
    }

    /**
     * Clear cached source-stack lookups for a specific room.
     * @param roomId the room ID
     */
    public void clearRoomSourceStackCache(int roomId) {
        String prefix = roomId + ":";
        sourceStacksByTriggerKey.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Clear all cached source-stack lookups.
     */
    public void clearAllSourceStackCache() {
        sourceStacksByTriggerKey.clear();
    }

    /**
     * Clear all execution-related caches for a specific room.
     * @param roomId the room ID
     */
    public void clearRoomExecutionCaches(int roomId) {
        clearRoomRecursionDepth(roomId);
        clearRoomRateLimiters(roomId);
        clearRoomSourceStackCache(roomId);
        clearRoomDiagnostics(roomId);
        clearRoomBan(roomId);
    }

    /**
     * Clear all execution-related caches.
     */
    public void clearAllExecutionCaches() {
        clearAllRecursionDepth();
        eventRateLimiters.clear();
        clearAllSourceStackCache();
        clearUnseenCache();
    }

    /**
     * Clear room ban for a specific room.
     * @param roomId the room ID
     */
    public void clearRoomBan(int roomId) {
        bannedRooms.remove(roomId);
    }

    /**
     * Get a monitor snapshot for a room.
     * @param roomId the room ID
     * @return the diagnostics snapshot
     */
    public WiredRoomDiagnostics.Snapshot getDiagnosticsSnapshot(int roomId) {
        long now = System.currentTimeMillis();
        long killedUntil = bannedRooms.getOrDefault(roomId, 0L);

        return getDiagnostics(roomId).snapshot(
                getRecursionDepth(roomId),
                MAX_RECURSION_DEPTH,
                killedUntil,
                now
        );
    }

    /**
     * Check if a room is currently banned from wired execution.
     * @param roomId the room ID
     * @return true if wired is banned in this room
     */
    private boolean isRoomBanned(int roomId) {
        Long banExpiry = bannedRooms.get(roomId);
        if (banExpiry == null) {
            return false;
        }

        if (System.currentTimeMillis() >= banExpiry) {
            bannedRooms.remove(roomId);
            return false;
        }

        return true;
    }

    /**
     * Ban wired execution in a room.
     * @param roomId the room ID
     * @param room the room object
     */
    private void banRoom(int roomId, Room room, WiredEvent.Type eventType, int eventCount) {
        getDiagnostics(roomId).recordKilled(
                System.currentTimeMillis(),
                String.format("Rate limit exceeded for %s with %d event(s) in %dms", eventType.name(), eventCount, RATE_LIMIT_WINDOW_MS),
                eventType.name(),
                0
        );

        // Only actually ban the room if ban duration is configured (> 0)
        if (WIRED_BAN_DURATION_MS > 0) {
            long banExpiry = System.currentTimeMillis() + WIRED_BAN_DURATION_MS;
            bannedRooms.put(roomId, banExpiry);

            long banMinutes = WIRED_BAN_DURATION_MS / 60000;

            // Send alert to all users in the room
            String roomAlertMessage = Emulator.getTexts().getValue("wired.abuse.room.alert")
                    .replace("%minutes%", String.valueOf(banMinutes));
            room.sendComposer(new GenericAlertComposer(roomAlertMessage).compose());

            // Send scripter bubble alert to staff with room link
            THashMap<String, String> keys = new THashMap<>();
            keys.put("title", Emulator.getTexts().getValue("wired.abuse.staff.title"));
            keys.put("message", Emulator.getTexts().getValue("wired.abuse.staff.message")
                    .replace("%roomname%", room.getName())
                    .replace("%owner%", room.getOwnerName())
                    .replace("%minutes%", String.valueOf(banMinutes)));
            keys.put("linkUrl", "event:navigator/goto/" + roomId);
            keys.put("linkTitle", Emulator.getTexts().getValue("wired.abuse.staff.link"));
            Emulator.getGameEnvironment().getHabboManager().sendPacketToHabbosWithPermission(
                    new BubbleAlertComposer("admin.staffalert", keys).compose(),
                    "acc_modtool_room_info"
            );

            LOGGER.warn("Wired abuse detected in room {} ({}). Owner: {}. Wired banned for {} minutes.",
                    roomId, room.getName(), room.getOwnerName(), banMinutes);
        } else {
            // Ban duration is 0 - only log, do not spam alerts or put a ban entry
            LOGGER.warn("Wired rate limit exceeded in room {} ({}) for event {} ({} events). Ban disabled (wired.abuse.ban.duration.ms=0).",
                    roomId, room.getName(), eventType.name(), eventCount);
        }
    }

    /**
     * Check if an event should be rate-limited.
     * Uses a soft limiter only, without banning rooms.
     * @param roomId the room ID
     * @param room the room object
     * @param eventType the event type
     * @return true if the event should be blocked due to rate limiting
     */
    private boolean isRateLimited(int roomId, Room room, WiredEvent.Type eventType) {
        String key = roomId + ":" + eventType.name();
        long now = System.currentTimeMillis();

        EventRateTracker tracker = eventRateLimiters.compute(key, (k, existing) -> {
            if (existing == null) {
                return new EventRateTracker(now);
            }
            existing.recordEvent(now);
            return existing;
        });

        boolean limited = tracker.isRateLimited(now);
        if (limited && tracker.shouldBan(now)) {
            // First time hitting limit in this suppression window - ban the room
            banRoom(roomId, room, eventType, tracker.getEventCount());
        }
        return limited;
    }

    private WiredRoomDiagnostics getDiagnostics(int roomId) {
        return roomDiagnostics.computeIfAbsent(roomId, ignored -> new WiredRoomDiagnostics(
                MONITOR_USAGE_WINDOW_MS,
                MONITOR_USAGE_LIMIT,
                MONITOR_DELAYED_EVENTS_LIMIT,
                MONITOR_OVERLOAD_AVERAGE_MS,
                MONITOR_OVERLOAD_PEAK_MS,
                MONITOR_HEAVY_USAGE_PERCENT,
                MONITOR_HEAVY_CONSECUTIVE_WINDOWS,
                MONITOR_OVERLOAD_CONSECUTIVE_WINDOWS,
                MONITOR_HEAVY_DELAYED_PERCENT,
                200
        ));
    }

    private int estimateStackCost(WiredStack stack, int recursionDepth) {
        int cost = 1;

        if (stack == null) {
            return cost;
        }

        cost += Math.max(0, stack.conditions().size());

        for (IWiredEffect effect : stack.effects()) {
            if (effect == null) {
                continue;
            }

            cost += effect.isSelector() ? 2 : 3;

            if (effect.getDelay() > 0) {
                cost += 4;
            }
        }

        cost += Math.max(0, recursionDepth) * 2;

        return Math.max(1, cost);
    }

    private String getMonitorSourceLabel(HabboItem triggerItem, WiredEvent event) {
        if (triggerItem != null && triggerItem.getBaseItem() != null && triggerItem.getBaseItem().getInteractionType() != null) {
            return triggerItem.getBaseItem().getInteractionType().getName();
        }

        return (event != null && event.getType() != null) ? event.getType().name() : "room";
    }

    private int getMonitorSourceId(HabboItem triggerItem) {
        return triggerItem != null ? triggerItem.getId() : 0;
    }

    private String buildStackMonitorReason(WiredStack stack, WiredEvent event, int stackCost) {
        if (stack == null) {
            return String.format("Processing %s with estimated cost %d", event.getType().name(), stackCost);
        }

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
                stackCost
        );
    }

    private String buildExecutionMonitorReason(WiredStack stack, long elapsedMs) {
        if (stack == null) {
            return String.format("Execution completed in %dms", elapsedMs);
        }

        return String.format(
                "Stack with %d condition(s) and %d effect(s) completed in %dms",
                stack.conditions().size(),
                stack.effects().size(),
                elapsedMs
        );
    }

    /**
     * Tracks event rate for a specific room + event type combination.
     */
    private static final class EventRateTracker {
        private long windowStart;
        private int eventCount;
        private boolean warned;

        EventRateTracker(long now) {
            this.windowStart = now;
            this.eventCount = 1;
            this.warned = false;
        }

        synchronized void recordEvent(long now) {
            if (now - windowStart > RATE_LIMIT_WINDOW_MS) {
                windowStart = now;
                eventCount = 1;
                warned = false;
            } else {
                eventCount++;
            }
        }

        synchronized boolean isRateLimited(long now) {
            return eventCount > MAX_EVENTS_PER_WINDOW;
        }

        synchronized boolean shouldBan(long now) {
            if (eventCount > MAX_EVENTS_PER_WINDOW && !warned) {
                warned = true;
                return true;
            }
            return false;
        }

        synchronized int getEventCount() {
            return eventCount;
        }
    }
}
