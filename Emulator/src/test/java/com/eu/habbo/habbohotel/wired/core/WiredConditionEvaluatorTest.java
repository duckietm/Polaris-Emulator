package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraOrEval;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.WiredConditionOperator;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredConditionEvaluatorTest {

    @Test
    void preservesPhysicalOrderOrGroupingStepAccountingAndNegation() {
        Room room = mock(Room.class);
        WiredServices services = mock(WiredServices.class);
        List<String> evaluationOrder = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();

        WiredStack stack = stack(List.of(
                new TestCondition("and", true, WiredConditionOperator.AND, evaluationOrder),
                new TestCondition("or-false", false, WiredConditionOperator.OR, evaluationOrder),
                new TestCondition("or-true", true, WiredConditionOperator.OR, evaluationOrder)));

        WiredState normalState = new WiredState(10);
        WiredContext normalContext = context(room, services, stack, normalState);
        WiredConditionEvaluator evaluator =
                new WiredConditionEvaluator((ignoredRoom, format, arguments) -> diagnostics.add(format));

        assertTrue(evaluator.outcomeForExecution(stack, normalContext, false));
        assertEquals(List.of("and", "or-false", "or-true"), evaluationOrder);
        assertEquals(3, normalState.steps());
        assertTrue(diagnostics.contains("Conditions result: {}"));

        evaluationOrder.clear();
        WiredState negatedState = new WiredState(10);
        assertFalse(evaluator.outcomeForExecution(stack, context(room, services, stack, negatedState), true));
        assertEquals(List.of("and", "or-false", "or-true"), evaluationOrder);
        assertEquals(3, negatedState.steps());
    }

    @Test
    void preservesNoConditionNegationWithoutConsumingSteps() {
        Room room = mock(Room.class);
        WiredServices services = mock(WiredServices.class);
        WiredStack stack = stack(List.of());
        WiredState state = new WiredState(10);
        WiredConditionEvaluator evaluator = new WiredConditionEvaluator((ignoredRoom, format, arguments) -> {});

        assertFalse(evaluator.outcomeForExecution(stack, context(room, services, stack, state), true));
        assertEquals(0, state.steps());
    }

    @Test
    void theEvaluationAddOnModeAppliesToItsPickedConditionsAndTheOthersMustAllPass() {
        Room room = mock(Room.class);
        com.eu.habbo.habbohotel.rooms.RoomSpecialTypes types =
                mock(com.eu.habbo.habbohotel.rooms.RoomSpecialTypes.class);
        org.mockito.Mockito.when(room.getRoomSpecialTypes()).thenReturn(types);
        WiredExtraOrEval orEval = mock(WiredExtraOrEval.class);
        org.mockito.Mockito.when(orEval.getEvaluatedConditionIds()).thenReturn(java.util.Set.of(1, 2));
        org.mockito.Mockito.when(
                        types.getExtras(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.Set.of(orEval));
        com.eu.habbo.habbohotel.users.HabboItem trigger = mock(com.eu.habbo.habbohotel.users.HabboItem.class);
        WiredServices services = mock(WiredServices.class);
        WiredConditionEvaluator evaluator = new WiredConditionEvaluator((ignoredRoom, format, arguments) -> {});

        // "At least one" over the picked 1 and 2; condition 3 is not picked and must pass on its own.
        WiredStack passing = new WiredStack(
                trigger,
                null,
                List.of(box(1, false), box(2, true), box(3, true)),
                List.of(),
                WiredExtraOrEval.MODE_AT_LEAST_ONE,
                1,
                false,
                false,
                false);
        assertTrue(evaluator.outcomeForExecution(passing, context(room, services, passing, new WiredState(10)), false));

        WiredStack failingOther = new WiredStack(
                trigger,
                null,
                List.of(box(1, false), box(2, true), box(3, false)),
                List.of(),
                WiredExtraOrEval.MODE_AT_LEAST_ONE,
                1,
                false,
                false,
                false);
        assertFalse(evaluator.outcomeForExecution(
                failingOther, context(room, services, failingOther, new WiredState(10)), false));

        // Without picks the mode covers every condition, so one passing condition is enough.
        org.mockito.Mockito.when(orEval.getEvaluatedConditionIds()).thenReturn(java.util.Set.of());
        assertTrue(evaluator.outcomeForExecution(
                failingOther, context(room, services, failingOther, new WiredState(10)), false));
    }

    private static IWiredCondition box(int id, boolean result) {
        com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition condition =
                mock(com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition.class);
        org.mockito.Mockito.when(condition.getId()).thenReturn(id);
        org.mockito.Mockito.when(condition.evaluate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(result);
        org.mockito.Mockito.when(condition.operator()).thenReturn(WiredConditionOperator.AND);
        return condition;
    }

    private static WiredStack stack(List<IWiredCondition> conditions) {
        return new WiredStack(null, null, conditions, List.of(), WiredExtraOrEval.MODE_ALL, 1, false, false, false);
    }

    private static WiredContext context(Room room, WiredServices services, WiredStack stack, WiredState state) {
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build();
        return new WiredContext(event, null, stack, services, state, null);
    }

    @Test
    void onlyARunningStackHandsBackItsPassedConditionsToRecord() {
        Room room = mock(Room.class);
        WiredServices services = mock(WiredServices.class);
        List<String> order = new ArrayList<>();
        TestCondition passing = new TestCondition("pass", true, WiredConditionOperator.AND, order);
        TestCondition failing = new TestCondition("fail", false, WiredConditionOperator.AND, order);
        WiredConditionEvaluator evaluator = new WiredConditionEvaluator((ignoredRoom, format, arguments) -> {});

        WiredStack runs = stack(List.of(passing));
        WiredConditionEvaluator.Outcome ran =
                evaluator.evaluateForExecution(runs, context(room, services, runs, new WiredState(10)), false);
        assertTrue(ran.execute());
        assertEquals(List.of(passing), ran.passed());

        // The passing condition is not handed back when another one stops the stack.
        WiredStack stopped = stack(List.of(passing, failing));
        WiredConditionEvaluator.Outcome blocked =
                evaluator.evaluateForExecution(stopped, context(room, services, stopped, new WiredState(10)), false);
        assertFalse(blocked.execute());
        assertTrue(blocked.passed().isEmpty());

        // A negated stack runs when its conditions fail and records none of them.
        WiredConditionEvaluator.Outcome negated =
                evaluator.evaluateForExecution(stopped, context(room, services, stopped, new WiredState(10)), true);
        assertTrue(negated.execute());
        assertTrue(negated.passed().isEmpty());
    }

    private static final class TestCondition implements IWiredCondition {
        private final String name;
        private final boolean result;
        private final WiredConditionOperator operator;
        private final List<String> evaluationOrder;

        private TestCondition(
                String name, boolean result, WiredConditionOperator operator, List<String> evaluationOrder) {
            this.name = name;
            this.result = result;
            this.operator = operator;
            this.evaluationOrder = evaluationOrder;
        }

        @Override
        public boolean evaluate(WiredContext context) {
            this.evaluationOrder.add(this.name);
            return this.result;
        }

        @Override
        public WiredConditionOperator operator() {
            return this.operator;
        }
    }
}
