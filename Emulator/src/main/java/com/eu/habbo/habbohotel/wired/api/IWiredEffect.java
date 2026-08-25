package com.eu.habbo.habbohotel.wired.api;

import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSimulation;

/**
 * Interface for wired effects in the new context-driven architecture.
 * <p>
 * Effects are the actions performed when a trigger fires and all conditions pass.
 * They receive a {@link WiredContext} containing all relevant data and should use
 * {@link WiredContext#services()} for all side effects.
 * </p>
 * 
 * <h3>Best Practices:</h3>
 * <ul>
 *   <li>Use {@code ctx.services()} for all room mutations (teleport, toggle, etc.)</li>
 *   <li>Use {@code ctx.targets()} to get users/items to affect</li>
 *   <li>Check {@code ctx.actor()} before operations requiring a user</li>
 *   <li>Call {@code ctx.state().step()} before expensive operations (automatic in engine)</li>
 * </ul>
 * 
 * <h3>Simulation Mode:</h3>
 * <p>
 * When WiredExtraRequireFullExecution is present, movement effects are first run in
 * simulation mode via {@link #simulate(WiredContext, WiredSimulation)}. Movement effects
 * should record their intended position changes to the simulation. If all effects pass
 * simulation, they are then executed for real.
 * </p>
 * 
 * @see WiredContext
 * @see WiredSimulation
 * @see IWiredTrigger
 * @see IWiredCondition
 */
public interface IWiredEffect {

    /**
     * Execute this effect with the given context.
     * 
     * @param ctx the wired context containing event data, room, actor, services, etc.
     */
    void execute(WiredContext ctx);
    
    /**
     * Get the delay in ticks (500ms each) before this effect executes.
     * Default is 0 (immediate execution).
     * 
     * @return delay in 500ms ticks
     */
    default int getDelay() {
        return 0;
    }
    
    /**
     * Check if this effect requires an actor (RoomUnit) to execute.
     * If true and no actor is present, the effect will be skipped.
     * Default is false for backwards compatibility.
     * 
     * @return true if an actor is required
     */
    default boolean requiresActor() {
        return false;
    }
    
    /**
     * Get the cooldown for this effect in milliseconds.
     * The effect won't execute again until the cooldown expires.
     * Default is 0 (no cooldown).
     * 
     * @return cooldown in milliseconds
     */
    default long getCooldown() {
        return 0L;
    }

    default boolean isSelector() {
        return false;
    }

    /**
     * Selectors can use this to gate stack execution after their target list has
     * been resolved. Returning false stops the stack before conditions/effects.
     */
    default boolean hasRequiredSelectorTargets(WiredContext ctx) {
        return true;
    }

    /**
     * Selectors that filter the current selector result should run after
     * selectors that create/replace that result.
     */
    default boolean usesExistingSelectorTargets() {
        return false;
    }

    /**
     * Simulate this effect's execution and record intended state changes.
     * <p>
     * This method is called when WiredExtraRequireFullExecution is present.
     * Movement effects should record their intended position changes to the
     * simulation WITHOUT modifying the real room. The simulation tracks cumulative
     * position changes, so if Effect 1 moves an item to tile X, Effect 2 will see
     * the item at tile X when calculating its move.
     * </p>
     * <p>
     * If this effect would fail (e.g., moving to a hole), call 
     * {@code simulation.fail("reason")} and return false.
     * </p>
     * <p>
     * The default implementation returns true (assumes success). Only movement
     * effects need to override this method.
     * </p>
     * 
     * @param ctx the wired context
     * @param simulation the simulation state tracker for recording moves
     * @return true if simulation succeeded, false if the move would fail
     */
    default boolean simulate(WiredContext ctx, WiredSimulation simulation) {
        // Default: effect doesn't involve movement, assume success
        return true;
    }
}
