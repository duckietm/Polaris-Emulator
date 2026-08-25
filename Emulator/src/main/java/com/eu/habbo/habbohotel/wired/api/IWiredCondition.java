package com.eu.habbo.habbohotel.wired.api;

import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

/**
 * Interface for wired conditions in the new context-driven architecture.
 * <p>
 * Conditions are evaluated after a trigger matches to determine if effects
 * should execute. All conditions must pass (AND logic by default) unless
 * modified by extras like WiredExtraOrEval.
 * </p>
 * 
 * <h3>Evaluation:</h3>
 * <ul>
 *   <li>Conditions receive a {@link WiredContext} with all relevant data</li>
 *   <li>Return true if the condition passes, false to block execution</li>
 *   <li>Use {@code ctx.actor()} for the triggering user</li>
 *   <li>Use {@code ctx.room()} for room state checks</li>
 * </ul>
 * 
 * <h3>Example Implementation:</h3>
 * <pre>{@code
 * public class UserHasEffectCondition implements IWiredCondition {
 *     private final int requiredEffectId;
 *     
 *     public boolean evaluate(WiredContext ctx) {
 *         return ctx.actor()
 *             .map(user -> user.getEffectId() == requiredEffectId)
 *             .orElse(false);
 *     }
 * }
 * }</pre>
 * 
 * @see WiredContext
 * @see IWiredTrigger
 * @see IWiredEffect
 */
public interface IWiredCondition {

    /**
     * Evaluate this condition against the current context.
     * 
     * @param ctx the wired context containing event data, room, actor, etc.
     * @return true if the condition passes, false to block effect execution
     */
    boolean evaluate(WiredContext ctx);
    
    /**
     * Get the operator for combining this condition with others.
     * Default is AND, meaning all conditions must pass.
     * 
     * @return the condition operator
     */
    default WiredConditionOperator operator() {
        return WiredConditionOperator.AND;
    }
}
