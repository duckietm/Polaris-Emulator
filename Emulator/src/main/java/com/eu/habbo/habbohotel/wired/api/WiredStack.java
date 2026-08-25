package com.eu.habbo.habbohotel.wired.api;

import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.Collections;
import java.util.List;

/**
 * Represents a wired stack - a trigger with its associated conditions and effects.
 * <p>
 * In Habbo, wired items stacked on the same tile form a logical unit:
 * <ul>
 *   <li>One trigger at the base (what starts the chain)</li>
 *   <li>Zero or more conditions (requirements that must be met)</li>
 *   <li>One or more effects (actions to perform)</li>
 *   <li>Optional extras (modifiers like random selection)</li>
 * </ul>
 * </p>
 * 
 * <h3>Execution Flow:</h3>
 * <ol>
 *   <li>Trigger receives an event and calls {@link IWiredTrigger#matches}</li>
 *   <li>If matched, all conditions are evaluated via {@link IWiredCondition#evaluate}</li>
 *   <li>If conditions pass, effects execute via {@link IWiredEffect#execute}</li>
 * </ol>
 * 
 * @see IWiredTrigger
 * @see IWiredCondition
 * @see IWiredEffect
 */
public final class WiredStack {
    
    private final HabboItem triggerItem;
    private final IWiredTrigger trigger;
    private final List<IWiredCondition> conditions;
    private final List<IWiredEffect> effects;
    
    // Extra modifiers
    private final int conditionEvaluationMode; // WiredExtraOrEval mode
    private final int conditionEvaluationValue; // WiredExtraOrEval numeric threshold
    private final boolean useRandom;        // WiredExtraRandom present
    private final boolean useUnseen;        // WiredExtraUnseen present
    private final boolean executeInOrder;   // WiredExtraExecuteInOrder present

    /**
     * Create a new wired stack.
     * 
     * @param triggerItem the wired trigger furniture item
     * @param trigger the trigger implementation
     * @param conditions list of conditions (may be empty)
     * @param effects list of effects (should have at least one)
     */
    public WiredStack(HabboItem triggerItem,
                      IWiredTrigger trigger,
                      List<IWiredCondition> conditions,
                      List<IWiredEffect> effects) {
        this(triggerItem, trigger, conditions, effects, 0, 1, false, false, false);
    }

    /**
     * Create a new wired stack with modifiers.
     * 
     * @param triggerItem the wired trigger furniture item
     * @param trigger the trigger implementation
     * @param conditions list of conditions
     * @param effects list of effects
     * @param conditionEvaluationMode condition evaluation mode from WiredExtraOrEval
     * @param conditionEvaluationValue numeric comparison value from WiredExtraOrEval
     * @param useRandom if true, select one random effect instead of all
     * @param useUnseen if true, execute effects in "unseen" order (round-robin)
     * @param executeInOrder if true, execute all regular effects in stable stack order
     */
    public WiredStack(HabboItem triggerItem,
                      IWiredTrigger trigger,
                      List<IWiredCondition> conditions,
                      List<IWiredEffect> effects,
                      int conditionEvaluationMode,
                      int conditionEvaluationValue,
                      boolean useRandom,
                      boolean useUnseen,
                      boolean executeInOrder) {
        this.triggerItem = triggerItem;
        this.trigger = trigger;
        this.conditions = conditions != null ? Collections.unmodifiableList(conditions) : Collections.emptyList();
        this.effects = effects != null ? Collections.unmodifiableList(effects) : Collections.emptyList();
        this.conditionEvaluationMode = conditionEvaluationMode;
        this.conditionEvaluationValue = conditionEvaluationValue;
        this.useRandom = useRandom;
        this.useUnseen = useUnseen;
        this.executeInOrder = executeInOrder;
    }

    /**
     * Get the wired trigger furniture item.
     * @return the trigger item
     */
    public HabboItem triggerItem() {
        return triggerItem;
    }

    /**
     * Get the trigger implementation.
     * @return the trigger
     */
    public IWiredTrigger trigger() {
        return trigger;
    }

    /**
     * Get the list of conditions.
     * @return unmodifiable list of conditions
     */
    public List<IWiredCondition> conditions() {
        return conditions;
    }

    /**
     * Get the list of effects.
     * @return unmodifiable list of effects
     */
    public List<IWiredEffect> effects() {
        return effects;
    }

    /**
     * Check if this stack has any conditions.
     * @return true if there are conditions
     */
    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    /**
     * Check if this stack has any effects.
     * @return true if there are effects
     */
    public boolean hasEffects() {
        return !effects.isEmpty();
    }

    /**
     * Get the condition evaluation mode from WiredExtraOrEval.
     * @return evaluation mode code
     */
    public int conditionEvaluationMode() {
        return conditionEvaluationMode;
    }

    /**
     * Get the condition evaluation numeric value from WiredExtraOrEval.
     * @return comparison value
     */
    public int conditionEvaluationValue() {
        return conditionEvaluationValue;
    }

    /**
     * Check if random mode is enabled (WiredExtraRandom).
     * When true, only one random effect is executed.
     * @return true if random mode is enabled
     */
    public boolean useRandom() {
        return useRandom;
    }

    /**
     * Check if unseen mode is enabled (WiredExtraUnseen).
     * When true, effects execute in round-robin order.
     * @return true if unseen mode is enabled
     */
    public boolean useUnseen() {
        return useUnseen;
    }

    /**
     * Check if ordered execution mode is enabled (WiredExtraExecuteInOrder).
     * When true, all regular effects execute in stable stack order.
     * @return true if ordered execution is enabled
     */
    public boolean executeInOrder() {
        return executeInOrder;
    }

    /**
     * Get the number of conditions.
     * @return condition count
     */
    public int conditionCount() {
        return conditions.size();
    }

    /**
     * Get the number of effects.
     * @return effect count
     */
    public int effectCount() {
        return effects.size();
    }

    @Override
    public String toString() {
        return "WiredStack{" +
                "triggerItem=" + (triggerItem != null ? triggerItem.getId() : "null") +
                ", trigger=" + (trigger != null ? trigger.listensTo() : "null") +
                ", conditions=" + conditions.size() +
                ", effects=" + effects.size() +
                ", conditionEvaluationMode=" + conditionEvaluationMode +
                ", conditionEvaluationValue=" + conditionEvaluationValue +
                ", random=" + useRandom +
                ", unseen=" + useUnseen +
                ", executeInOrder=" + executeInOrder +
                '}';
    }
}
