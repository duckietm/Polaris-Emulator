package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraOrEval;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Internal owner of WIRED condition grouping, evaluation modes and negation.
 *
 * <p>The engine remains the public execution surface. This collaborator deliberately receives the
 * existing mutable execution context and evaluates conditions in the same physical order so step
 * accounting, plugin-visible condition instances and diagnostics remain behavior-compatible.
 */
final class WiredConditionEvaluator {

    @FunctionalInterface
    interface DiagnosticSink {
        void log(Room room, String format, Object... arguments);
    }

    private final DiagnosticSink diagnostics;

    WiredConditionEvaluator(DiagnosticSink diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    boolean outcomeForExecution(WiredStack stack, WiredContext context, boolean negateConditions) {
        if (!stack.hasConditions()) {
            return !negateConditions;
        }

        boolean conditionsPassed = evaluate(stack, context);
        diagnostics.log(context.room(), "Conditions result: {}", conditionsPassed ? "PASSED" : "FAILED");
        return negateConditions ? !conditionsPassed : conditionsPassed;
    }

    private boolean evaluate(WiredStack stack, WiredContext context) {
        Set<Integer> evaluated = evaluatedConditionIds(stack, context.room());
        if (evaluated.isEmpty()) {
            return evaluateByMode(
                    stack.conditions(), context, stack.conditionEvaluationMode(), stack.conditionEvaluationValue());
        }

        // The evaluation add-on's mode covers the conditions picked in it; every other condition must still pass.
        List<IWiredCondition> picked = new ArrayList<>();
        List<IWiredCondition> others = new ArrayList<>();
        for (IWiredCondition condition : stack.conditions()) {
            if (condition instanceof InteractionWiredCondition box && evaluated.contains(box.getId())) {
                picked.add(condition);
            } else {
                others.add(condition);
            }
        }

        // Both groups are evaluated, so every passing condition flashes as before.
        boolean othersPassed = evaluateByMode(others, context, WiredExtraOrEval.MODE_ALL, 1);
        boolean pickedPassed =
                evaluateByMode(picked, context, stack.conditionEvaluationMode(), stack.conditionEvaluationValue());
        return othersPassed && pickedPassed;
    }

    /** The conditions the stack's evaluation add-on picked; empty when its mode applies to all of them. */
    private static Set<Integer> evaluatedConditionIds(WiredStack stack, Room room) {
        HabboItem trigger = stack.triggerItem();
        if (trigger == null || room == null || room.getRoomSpecialTypes() == null) return Set.of();

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras(trigger.getX(), trigger.getY())) {
            if (extra instanceof WiredExtraOrEval orEval) return orEval.getEvaluatedConditionIds();
        }
        return Set.of();
    }

    private boolean evaluateByMode(
            List<IWiredCondition> conditions, WiredContext context, int evaluationMode, int evaluationValue) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        Room room = context.room();
        Map<String, List<Boolean>> groupedOrResults = new LinkedHashMap<>();
        int matchedRequirements = 0;
        int totalRequirements = 0;

        for (IWiredCondition condition : conditions) {
            context.state().step();

            boolean result = condition.evaluate(context);
            String conditionKey = conditionGroupKey(condition);

            // A passing condition flashes with its trigger; a failed one stays at rest.
            if (result && condition instanceof InteractionWiredCondition conditionBox) {
                conditionBox.flashBox(room);
            }

            if (condition.operator() == WiredConditionOperator.OR) {
                groupedOrResults
                        .computeIfAbsent(conditionKey, ignored -> new ArrayList<>())
                        .add(result);
                diagnostics.log(
                        room,
                        "  Condition (OR group {}) {}: {}",
                        conditionKey,
                        condition.getClass().getSimpleName(),
                        result ? "PASS" : "FAIL");
                continue;
            }

            totalRequirements++;

            if (result) {
                matchedRequirements++;
            }

            diagnostics.log(room, "  Condition {}: {}", condition.getClass().getSimpleName(), result ? "PASS" : "FAIL");
        }

        for (Map.Entry<String, List<Boolean>> entry : groupedOrResults.entrySet()) {
            totalRequirements++;

            boolean groupPassed = entry.getValue().stream().anyMatch(Boolean::booleanValue);
            if (groupPassed) {
                matchedRequirements++;
            }

            diagnostics.log(room, "  Condition (OR result {}) : {}", entry.getKey(), groupPassed ? "PASS" : "FAIL");
        }

        boolean matches =
                WiredExtraOrEval.matchesMode(evaluationMode, matchedRequirements, totalRequirements, evaluationValue);

        diagnostics.log(
                room,
                "Condition eval mode {} value {} matched {}/{} logical requirements => {}",
                evaluationMode,
                evaluationValue,
                matchedRequirements,
                totalRequirements,
                matches ? "PASS" : "FAIL");
        return matches;
    }

    private static String conditionGroupKey(IWiredCondition condition) {
        if (condition instanceof InteractionWiredCondition interactionCondition) {
            return String.valueOf(interactionCondition.getType());
        }

        return condition.getClass().getName();
    }
}
