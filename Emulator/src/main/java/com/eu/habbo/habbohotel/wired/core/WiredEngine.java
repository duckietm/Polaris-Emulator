package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecutionLimit;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRandom;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUnseen;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksUser;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboSaysKeyword;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericAlertComposer;
import com.eu.habbo.messages.outgoing.rooms.items.ItemStateComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public static volatile int MAX_RECURSION_DEPTH = 10;

    /** Maximum events of same type per room within rate limit window before banning */
    public static volatile int MAX_EVENTS_PER_WINDOW = 100;

    /** Time window for counting rapid events (milliseconds) */
    public static volatile long RATE_LIMIT_WINDOW_MS = 10000;

    /** Duration to ban wired execution in a room after abuse detected (milliseconds) */
    public static volatile long WIRED_BAN_DURATION_MS = 600000;

    /** Monitor usage window in milliseconds */
    public static volatile int MONITOR_USAGE_WINDOW_MS = 1000;

    /** Monitor execution cap per room window */
    public static volatile int MONITOR_USAGE_LIMIT = 50000;

    /** Maximum delayed events allowed per room at the same time */
    public static volatile int MONITOR_DELAYED_EVENTS_LIMIT = 50000;

    /** Average execution threshold that marks overload */
    public static volatile int MONITOR_OVERLOAD_AVERAGE_MS = 50;

    /** Peak execution threshold that marks overload */
    public static volatile int MONITOR_OVERLOAD_PEAK_MS = 150;

    /** Consecutive overloaded windows required before recording overload */
    public static volatile int MONITOR_OVERLOAD_CONSECUTIVE_WINDOWS = 2;

    /** Usage percentage threshold that marks a room as heavy */
    public static volatile int MONITOR_HEAVY_USAGE_PERCENT = 70;

    /** Consecutive windows above threshold before marking heavy */
    public static volatile int MONITOR_HEAVY_CONSECUTIVE_WINDOWS = 5;

    /** Delayed queue percentage threshold that contributes to heavy state */
    public static volatile int MONITOR_HEAVY_DELAYED_PERCENT = 60;

    private final WiredServices services;
    private final WiredStackIndex index;
    private final int maxStepsPerStack;
    private final WiredConditionEvaluator conditionEvaluator;
    private final WiredDelayedScheduler delayedScheduler;
    private final WiredEffectCooldownService effectCooldownService;
    private final WiredEventDispatcher eventDispatcher;
    private final WiredEffectPlanner effectPlanner;
    private final WiredExecutionGuard executionGuard;
    private final WiredStackExecutor stackExecutor;
    private final WiredStackRepository stackRepository;

    /** Track unseen effect indices per room+tile for round-robin selection */
    private final ConcurrentHashMap<String, Integer> unseenIndices;

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
        this(
                services,
                index,
                maxStepsPerStack,
                roomId -> WiredPlatform.gameEnvironment().getRoomManager().getRoom(roomId));
    }

    WiredEngine(
            WiredServices services,
            WiredStackIndex index,
            int maxStepsPerStack,
            WiredDelayedScheduler.RoomResolver delayedRoomResolver) {
        if (services == null) throw new IllegalArgumentException("Services cannot be null");
        if (index == null) throw new IllegalArgumentException("Index cannot be null");
        if (maxStepsPerStack <= 0) throw new IllegalArgumentException("Max steps must be positive");

        this.services = services;
        this.index = index;
        this.maxStepsPerStack = maxStepsPerStack;
        this.conditionEvaluator = new WiredConditionEvaluator(this::debug);
        this.delayedScheduler = new WiredDelayedScheduler(
                (task, delayMs) -> {
                    var future = WiredPlatform.threading().run(task, delayMs);
                    return future == null ? null : () -> future.cancel(false);
                },
                System::currentTimeMillis,
                this::debug,
                delayedRoomResolver);
        this.effectCooldownService = new WiredEffectCooldownService(() -> WiredPlatform.configuration() != null
                && WiredPlatform.configuration().getBoolean("wired.custom.enabled", false));
        this.effectPlanner = new WiredEffectPlanner();
        this.stackRepository = new WiredStackRepository(index);
        this.executionGuard =
                new WiredExecutionGuard(System::currentTimeMillis, this::handleRateLimit, this::handleRecursionLimit);
        this.stackExecutor = new WiredStackExecutor(
                this.services,
                this.maxStepsPerStack,
                this.conditionEvaluator,
                this.effectPlanner,
                this.executionGuard,
                new WiredStackExecutor.Hooks() {
                    @Override
                    public List<InteractionWiredEffect> executeSelectors(WiredStack stack, WiredContext context) {
                        return WiredEngine.this.executeSelectors(stack, context);
                    }

                    @Override
                    public void applySelectionFilterExtras(
                            WiredStack stack, WiredContext context, List<InteractionWiredEffect> executedSelectors) {
                        WiredEngine.this.applySelectionFilterExtras(stack, context, executedSelectors);
                    }

                    @Override
                    public boolean selectorsHaveRequiredTargets(
                            List<InteractionWiredEffect> executedSelectors, WiredContext context) {
                        return WiredEngine.this.selectorsHaveRequiredTargets(executedSelectors, context);
                    }

                    @Override
                    public void finalizeSelectors(
                            List<InteractionWiredEffect> executedSelectors, WiredContext context, long currentTime) {
                        WiredEngine.this.finalizeSelectors(executedSelectors, context, currentTime);
                    }

                    @Override
                    public void executeEffects(
                            WiredStack stack, List<IWiredEffect> effects, WiredContext context, long currentTime) {
                        WiredEngine.this.executeEffects(stack, effects, context, currentTime);
                    }
                },
                System::currentTimeMillis,
                this::debug,
                WiredStructuredDiagnostics.production());
        this.eventDispatcher = new WiredEventDispatcher(
                this.executionGuard, this.stackRepository, this.stackExecutor::executeEvent, this::debug);
        this.unseenIndices = new ConcurrentHashMap<>();
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
        return this.eventDispatcher.dispatch(event, negateConditions);
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
        return this.eventDispatcher.dispatchForSourceItem(event, sourceItemId);
    }

    public boolean executeDirectStack(WiredStack stack, WiredEvent event, boolean negateConditions) {
        return this.stackExecutor.executeDirect(stack, event, negateConditions);
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

        boolean conditionsPassedForExecution =
                this.conditionEvaluator.outcomeForExecution(stack, ctx, negateConditions);
        List<IWiredEffect> executableEffects =
                this.effectPlanner.executableEffects(stack, conditionsPassedForExecution);
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

        boolean conditionsPassedForExecution = this.conditionEvaluator.outcomeForExecution(stack, ctx, false);
        if (!conditionsPassedForExecution) {
            return false;
        }

        List<IWiredEffect> executableEffects = this.effectPlanner.executableEffects(stack, true);
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

    private WiredTextInputCaptureSupport.CaptureResult resolveTextInputCapture(WiredStack stack, WiredEvent event) {
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
                debug(
                        ctx.room(),
                        "Random mode: selected {} effect(s), skip window {}",
                        toExecute.size(),
                        randomExtra.getSkipExecutions());
            } else {
                int randomIndex = selectRandomIndex(effects.size());
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

        try (WiredInternalVariableSupport.UserMoveBatchScope ignored =
                WiredInternalVariableSupport.beginUserMoveBatch()) {
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
                    if (!this.effectCooldownService.tryAcquire(effect, ctx, currentTime)) {
                        continue;
                    }
                    ctx.state().step();
                    try {
                        WiredExecutionScope.execute(effect, ctx);

                        // Activate box animation after execution
                        if (effect instanceof InteractionWiredEffect) {
                            InteractionWiredEffect wiredEffect = (InteractionWiredEffect) effect;
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
        WiredEffectPlanner.SelectorPlan selectorPlan = this.effectPlanner.selectorPlan(effects);

        executeSelectorList(selectorPlan.immediate(), ctx, executedSelectors);
        executeSelectorList(selectorPlan.deferred(), ctx, executedSelectors);

        return executedSelectors;
    }

    private void executeSelectorList(
            List<IWiredEffect> selectors, WiredContext ctx, List<InteractionWiredEffect> executedSelectors) {
        for (IWiredEffect effect : selectors) {
            if (effect.requiresActor() && !ctx.hasActor()) {
                continue;
            }

            if (!this.effectCooldownService.tryAcquire(effect, ctx, ctx.event().getCreatedAtMs())) {
                continue;
            }

            ctx.state().step();
            try {
                WiredExecutionScope.execute(effect, ctx);
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

    private void scheduleFilteredSelectorState(
            Room room,
            InteractionWiredEffect wiredEffect,
            String state,
            long animationToken,
            long delay,
            boolean clearToken) {
        WiredPlatform.threading()
                .run(() -> setFilteredSelectorState(room, wiredEffect, state, animationToken, clearToken), delay);
    }

    private void setFilteredSelectorState(Room room, InteractionWiredEffect wiredEffect, String state) {
        setFilteredSelectorState(room, wiredEffect, state, 0L, false);
    }

    private void setFilteredSelectorState(
            Room room, InteractionWiredEffect wiredEffect, String state, long animationToken, boolean clearToken) {
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

    private void applySelectionFilterExtras(
            WiredStack stack, WiredContext ctx, List<InteractionWiredEffect> executedSelectors) {
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
        this.delayedScheduler.scheduleEffect(
                effect, ctx, delay, triggerTime, diagnostics, sourceLabel, sourceId, this::executeDelayedEffect);
    }

    private void executeDelayedEffect(WiredDelayedExecutionSnapshot.Resolved resolved) {
        IWiredEffect effect = resolved.effects().getFirst();
        WiredContext context = resolved.context();
        long executionTime = System.currentTimeMillis();
        if (!this.effectCooldownService.tryAcquire(effect, context, executionTime)) {
            return;
        }
        try {
            WiredExecutionScope.execute(effect, context);

            if (effect instanceof InteractionWiredEffect wiredEffect) {
                wiredEffect.activateBox(context.room(), context.actor().orElse(null), executionTime);
            }
        } catch (Exception exception) {
            LOGGER.warn("Error executing delayed effect: {}", exception.getMessage());
        }
    }

    private void executeOrderedEffects(List<IWiredEffect> effects, WiredContext ctx, long currentTime) {
        if (effects == null || effects.isEmpty()) {
            return;
        }

        for (WiredEffectPlanner.DelayBatch delayBatch :
                this.effectPlanner.orderedDelayBatches(effects, ctx.hasActor())) {
            int delay = delayBatch.delay();
            List<IWiredEffect> batch = delayBatch.effects();

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

        List<WiredStack> stacks = this.stackRepository.getStacks(room, event.getType());
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

        this.delayedScheduler.scheduleOrderedBatch(
                batch,
                ctx,
                delay,
                triggerTime,
                diagnostics,
                sourceLabel,
                sourceId,
                resolved -> executeOrderedEffectBatch(
                        resolved.effects(), resolved.context(), System.currentTimeMillis(), true));
    }

    private void executeOrderedEffectBatch(
            List<IWiredEffect> batch, WiredContext ctx, long executionTime, boolean useExecutionTimeForCooldown) {
        Room room = ctx.room();
        RoomUnit actor = ctx.actor().orElse(null);

        WiredMoveCarryHelper.beginMovementCollection();

        try (WiredInternalVariableSupport.UserMoveBatchScope ignored =
                WiredInternalVariableSupport.beginUserMoveBatch()) {
            for (IWiredEffect effect : batch) {
                try {
                    if (!this.effectCooldownService.tryAcquire(effect, ctx, executionTime)) {
                        continue;
                    }
                    if (!useExecutionTimeForCooldown) {
                        ctx.state().step();
                    }

                    WiredExecutionScope.execute(effect, ctx);

                    if (effect instanceof InteractionWiredEffect) {
                        InteractionWiredEffect wiredEffect = (InteractionWiredEffect) effect;
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
        String key =
                stack.triggerItem() != null ? String.valueOf(stack.triggerItem().getId()) : "default";

        return unseenIndices.compute(key, (k, current) -> {
            if (current == null) current = -1;
            return (current + 1) % effectCount;
        });
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

    private WiredExtraRandom getRandomExtra(Room room, WiredStack stack) {
        InteractionWiredExtra extra = getStackExtra(room, stack, WiredExtraRandom.class);

        return (extra instanceof WiredExtraRandom) ? (WiredExtraRandom) extra : null;
    }

    static int selectRandomIndex(int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    private WiredExtraUnseen getUnseenExtra(Room room, WiredStack stack) {
        InteractionWiredExtra extra = getStackExtra(room, stack, WiredExtraUnseen.class);

        return (extra instanceof WiredExtraUnseen) ? (WiredExtraUnseen) extra : null;
    }

    private WiredExtraExecutionLimit getExecutionLimitExtra(Room room, WiredStack stack) {
        InteractionWiredExtra extra = getStackExtra(room, stack, WiredExtraExecutionLimit.class);

        return (extra instanceof WiredExtraExecutionLimit) ? (WiredExtraExecutionLimit) extra : null;
    }

    private <T extends InteractionWiredExtra> InteractionWiredExtra getStackExtra(
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
        this.executionGuard.clearRoomRecursionDepth(roomId);
    }

    /**
     * Clear all recursion tracking.
     */
    public void clearAllRecursionDepth() {
        this.executionGuard.clearAllRecursionDepth();
    }

    /**
     * Get the current recursion depth for a room (for debugging).
     * @param roomId the room ID
     * @return the current recursion depth, or 0 if not tracked
     */
    public int getRecursionDepth(int roomId) {
        return this.executionGuard.recursionDepth(roomId);
    }

    /**
     * Clear rate limiters for a specific room.
     * Should be called when a room is unloaded.
     * @param roomId the room ID
     */
    public void clearRoomRateLimiters(int roomId) {
        this.executionGuard.clearRoomRateLimiters(roomId);
    }

    /**
     * Clear monitor diagnostics for a specific room.
     * @param roomId the room ID
     */
    public void clearRoomDiagnostics(int roomId) {
        this.executionGuard.clearRoomDiagnostics(roomId);
    }

    /**
     * Clear all monitor diagnostics.
     */
    public void clearAllDiagnostics() {
        this.executionGuard.clearAllDiagnostics();
    }

    public void clearRoomDiagnosticsLogs(int roomId) {
        this.executionGuard.clearRoomDiagnosticsLogs(roomId);
    }

    /**
     * Clear cached source-stack lookups for a specific room.
     * @param roomId the room ID
     */
    public void clearRoomSourceStackCache(int roomId) {
        this.stackRepository.clearRoomSourceStackCache(roomId);
    }

    /**
     * Clear all cached source-stack lookups.
     */
    public void clearAllSourceStackCache() {
        this.stackRepository.clearAllSourceStackCache();
    }

    int sourceStackCacheSize() {
        return this.stackRepository.sourceStackCacheSize();
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
        this.executionGuard.clearAllRateLimiters();
        clearAllSourceStackCache();
        clearUnseenCache();
        this.effectCooldownService.clear();
    }

    void shutdownScheduledWork() {
        this.delayedScheduler.shutdown();
    }

    boolean tryAcquireEffectCooldown(IWiredEffect effect, WiredContext context, long timestamp) {
        return this.effectCooldownService.tryAcquire(effect, context, timestamp);
    }

    /**
     * Clear room ban for a specific room.
     * @param roomId the room ID
     */
    public void clearRoomBan(int roomId) {
        this.executionGuard.clearRoomBan(roomId);
    }

    /**
     * Get a monitor snapshot for a room.
     * @param roomId the room ID
     * @return the diagnostics snapshot
     */
    public WiredRoomDiagnostics.Snapshot getDiagnosticsSnapshot(int roomId) {
        return this.executionGuard.snapshot(roomId);
    }

    private void handleRateLimit(
            Room room,
            WiredEvent.Type eventType,
            int eventCount,
            WiredExecutionGuard.LimitSource limits,
            boolean banned) {
        int roomId = room.getId();
        if (banned) {
            long banMinutes = limits.banDurationMs() / 60000;

            // Send alert to all users in the room
            String roomAlertMessage = Emulator.getTexts()
                    .getValue("wired.abuse.room.alert")
                    .replace("%minutes%", String.valueOf(banMinutes));
            room.sendComposer(new GenericAlertComposer(roomAlertMessage).compose());

            // Send scripter bubble alert to staff with room link
            Map<String, String> keys = new HashMap<>();
            keys.put("title", Emulator.getTexts().getValue("wired.abuse.staff.title"));
            keys.put(
                    "message",
                    Emulator.getTexts()
                            .getValue("wired.abuse.staff.message")
                            .replace("%roomname%", room.getName())
                            .replace("%owner%", room.getOwnerName())
                            .replace("%minutes%", String.valueOf(banMinutes)));
            keys.put("linkUrl", "event:navigator/goto/" + roomId);
            keys.put("linkTitle", Emulator.getTexts().getValue("wired.abuse.staff.link"));
            WiredPlatform.gameEnvironment()
                    .getHabboManager()
                    .sendPacketToHabbosWithPermission(
                            new BubbleAlertComposer("admin.staffalert", keys).compose(), "acc_modtool_room_info");

            LOGGER.warn(
                    "Wired abuse detected in room {} ({}). Owner: {}. Wired banned for {} minutes.",
                    roomId,
                    room.getName(),
                    room.getOwnerName(),
                    banMinutes);
        } else {
            LOGGER.warn(
                    "Wired rate limit exceeded in room {} ({}) for event {} ({} events). Ban disabled (wired.abuse.ban.duration.ms=0).",
                    roomId,
                    room.getName(),
                    eventType.name(),
                    eventCount);
        }
    }

    private void handleRecursionLimit(
            Room room,
            WiredEvent.Type eventType,
            WiredExecutionGuard.EntryKind kind,
            int currentDepth,
            int maximumDepth) {
        if (kind == WiredExecutionGuard.EntryKind.SOURCE_ITEM) {
            LOGGER.warn(
                    "Wired recursion limit reached in room {} (depth: {}). "
                            + "Possible infinite loop detected (source item execution). Aborting.",
                    room.getId(),
                    currentDepth);
            debug(room, "RECURSION LIMIT REACHED - aborting source-item execution");
            return;
        }

        LOGGER.warn(
                "Wired recursion limit reached in room {} (depth: {}). "
                        + "Possible infinite loop detected (e.g., collision + chase). Aborting.",
                room.getId(),
                currentDepth);
        debug(room, "RECURSION LIMIT REACHED - aborting to prevent crash");
    }

    private WiredRoomDiagnostics getDiagnostics(int roomId) {
        return this.executionGuard.diagnostics(roomId);
    }

    private String getMonitorSourceLabel(HabboItem triggerItem, WiredEvent event) {
        if (triggerItem != null
                && triggerItem.getBaseItem() != null
                && triggerItem.getBaseItem().getInteractionType() != null) {
            return triggerItem.getBaseItem().getInteractionType().getName();
        }

        return (event != null && event.getType() != null) ? event.getType().name() : "room";
    }

    private int getMonitorSourceId(HabboItem triggerItem) {
        return triggerItem != null ? triggerItem.getId() : 0;
    }
}
