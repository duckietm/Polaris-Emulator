package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.Optional;

/**
 * Context object passed to conditions and effects during wired execution.
 * <p>
 * This is the "backbone" of the new wired system. It provides:
 * <ul>
 *   <li>Access to the triggering event ({@link #event()})</li>
 *   <li>Convenience accessors for common event data</li>
 *   <li>Mutable target collection ({@link #targets()})</li>
 *   <li>Services for performing side effects ({@link #services()})</li>
 *   <li>Execution state for loop safety ({@link #state()})</li>
 * </ul>
 * </p>
 *
 * <h3>Usage in Conditions:</h3>
 * <pre>{@code
 * public boolean evaluate(WiredContext ctx) {
 *     return ctx.actor()
 *         .map(user -> user.getEffectId() == requiredEffect)
 *         .orElse(false);
 * }
 * }</pre>
 *
 * <h3>Usage in Effects:</h3>
 * <pre>{@code
 * public void execute(WiredContext ctx) {
 *     ctx.actor().ifPresent(user ->
 *         ctx.services().teleportUser(ctx.room(), user, targetTile));
 * }
 * }</pre>
 *
 * @see WiredEvent
 * @see WiredServices
 * @see WiredState
 * @see WiredTargets
 */
public final class WiredContext {

    private final WiredEvent event;
    private final WiredServices services;
    private final WiredState state;
    private final WiredTargets targets;

    /** Actor still eligible for the current execution; delayed work revalidates this reference. */
    private RoomUnit activeActor;

    /** The wired trigger furniture item executing this stack */
    private final HabboItem triggerItem;

    /** The wired stack being executed (for conditions to access effects) */
    private final WiredStack stack;

    /** Extra settings from the trigger item (for legacy compatibility) */
    private final Object[] legacySettings;

    /** Runtime-local context variables shared through the current execution chain. */
    private WiredContextVariableScope contextVariables;

    /** Whether selector item resolution should include wired furniture too. */
    private boolean includeWiredSelectorItems = false;

    /**
     * Create a new wired context.
     *
     * @param event the triggering event (required)
     * @param triggerItem the wired trigger item (may be null for programmatic triggers)
     * @param services the services for performing side effects
     * @param state the execution state for loop safety
     */
    public WiredContext(WiredEvent event, HabboItem triggerItem, WiredServices services, WiredState state) {
        this(event, triggerItem, null, services, state, null);
    }

    /**
     * Create a new wired context with legacy settings.
     *
     * @param event the triggering event (required)
     * @param triggerItem the wired trigger item (may be null)
     * @param services the services for performing side effects
     * @param state the execution state
     * @param legacySettings extra settings array for legacy adapter compatibility
     */
    public WiredContext(
            WiredEvent event,
            HabboItem triggerItem,
            WiredServices services,
            WiredState state,
            Object[] legacySettings) {
        this(event, triggerItem, null, services, state, legacySettings);
    }

    /**
     * Create a new wired context with stack and legacy settings.
     *
     * @param event the triggering event (required)
     * @param triggerItem the wired trigger item (may be null)
     * @param stack the wired stack being executed (may be null)
     * @param services the services for performing side effects
     * @param state the execution state
     * @param legacySettings extra settings array for legacy adapter compatibility
     */
    public WiredContext(
            WiredEvent event,
            HabboItem triggerItem,
            WiredStack stack,
            WiredServices services,
            WiredState state,
            Object[] legacySettings) {
        if (event == null) throw new IllegalArgumentException("Event cannot be null");
        if (services == null) throw new IllegalArgumentException("Services cannot be null");
        if (state == null) throw new IllegalArgumentException("State cannot be null");

        this.event = event;
        this.activeActor = event.getActor().orElse(null);
        this.triggerItem = triggerItem;
        this.stack = stack;
        this.services = services;
        this.state = state;
        this.legacySettings = legacySettings;
        this.contextVariables = (event.getContextVariableScope() != null)
                ? event.getContextVariableScope().copy()
                : new WiredContextVariableScope();
        this.targets = new WiredTargets();

        // Default targets: include actor and trigger item for backwards compatibility
        event.getActor().ifPresent(targets::addUser);
        if (triggerItem != null) {
            targets.addItem(triggerItem);
        }
    }

    // ========== Event Access ==========

    /**
     * Get the triggering event.
     * @return the wired event
     */
    public WiredEvent event() {
        return event;
    }

    /**
     * Get the event type.
     * @return the event type
     */
    public WiredEvent.Type eventType() {
        return event.getType();
    }

    // ========== Convenience Accessors ==========

    /**
     * Get the room where this event occurred.
     * @return the room (never null)
     */
    public Room room() {
        return event.getRoom();
    }

    /**
     * Get the actor (user) that caused this event.
     * @return optional containing the actor
     */
    public Optional<RoomUnit> actor() {
        return Optional.ofNullable(this.activeActor);
    }

    /**
     * Get the source item that was involved in this event.
     * @return optional containing the source item
     */
    public Optional<HabboItem> sourceItem() {
        return event.getSourceItem();
    }

    /**
     * Get the tile where this event occurred.
     * @return optional containing the tile
     */
    public Optional<RoomTile> tile() {
        return event.getTile();
    }

    /**
     * Get the text associated with this event.
     * @return optional containing the text
     */
    public Optional<String> text() {
        return event.getText();
    }

    // ========== Trigger Item ==========

    /**
     * Get the wired trigger item that initiated this execution.
     * This is different from {@link #sourceItem()} which is the item the user interacted with.
     * @return the trigger item, or null if this is a programmatic trigger
     */
    public HabboItem triggerItem() {
        return triggerItem;
    }

    /**
     * Check if there is a trigger item.
     * @return true if a trigger item is present
     */
    public boolean hasTriggerItem() {
        return triggerItem != null;
    }

    // ========== Stack ==========

    /**
     * Get the wired stack being executed.
     * This is useful for conditions that need to access all effects in the stack
     * (e.g., WiredConditionMovementValidation).
     * @return the wired stack, or null if not available
     */
    public WiredStack stack() {
        return stack;
    }

    /**
     * Check if there is a stack available.
     * @return true if a stack is present
     */
    public boolean hasStack() {
        return stack != null;
    }

    // ========== Targets ==========

    /**
     * Get the mutable targets collection.
     * Effects should use this to determine which users/items to affect.
     * Selectors (future feature) can modify this before effects run.
     * @return the targets collection
     */
    public WiredTargets targets() {
        return targets;
    }

    // ========== Services ==========

    /**
     * Get the services for performing side effects.
     * @return the wired services
     */
    public WiredServices services() {
        return services;
    }

    // ========== State ==========

    /**
     * Get the execution state.
     * @return the wired state
     */
    public WiredState state() {
        return state;
    }

    // ========== Legacy Support ==========

    /**
     * Get the legacy settings array for adapter compatibility.
     * This allows legacy effects/conditions to receive their expected parameters.
     * @return the legacy settings, or empty array if not set
     */
    public Object[] legacySettings() {
        return legacySettings != null ? legacySettings : new Object[0];
    }

    public WiredContextVariableScope contextVariables() {
        return this.contextVariables;
    }

    public void forkContextVariables() {
        this.contextVariables = this.contextVariables.copy();
    }

    public boolean includeWiredSelectorItems() {
        return this.includeWiredSelectorItems;
    }

    public void setIncludeWiredSelectorItems(boolean includeWiredSelectorItems) {
        this.includeWiredSelectorItems = includeWiredSelectorItems;
    }

    /** Restore copied delayed-only state after live room entities have been resolved. */
    void restoreDelayedSnapshot(
            WiredContextVariableScope contextVariables,
            Iterable<RoomUnit> users,
            Iterable<HabboItem> items,
            boolean usersModifiedBySelector,
            boolean itemsModifiedBySelector,
            boolean includeWiredSelectorItems) {
        this.contextVariables = contextVariables != null ? contextVariables.copy() : new WiredContextVariableScope();
        this.targets.restoreSnapshot(users, items, usersModifiedBySelector, itemsModifiedBySelector);
        this.includeWiredSelectorItems = includeWiredSelectorItems;
    }

    // ========== Utility Methods ==========

    /**
     * Check if there is an actor for this context.
     * @return true if an actor is present
     */
    public boolean hasActor() {
        return this.activeActor != null;
    }

    /**
     * Check if there is a source item for this context.
     * @return true if a source item is present
     */
    public boolean hasSourceItem() {
        return event.getSourceItem().isPresent();
    }

    /**
     * Check if there is text for this context.
     * @return true if text is present
     */
    public boolean hasText() {
        return event.getText().isPresent();
    }

    /**
     * Log a debug message through services.
     * @param message the message to log
     */
    public void debug(String message) {
        services.debug(room(), message);
    }

    /**
     * Log a debug message with format arguments.
     * @param format the format string
     * @param args the arguments
     */
    public void debug(String format, Object... args) {
        services.debug(room(), format, args);
    }

    @Override
    public String toString() {
        return "WiredContext{" + "event="
                + event + ", triggerItem="
                + (triggerItem != null ? triggerItem.getId() : "null") + ", targets="
                + targets + ", state="
                + state + '}';
    }
}
