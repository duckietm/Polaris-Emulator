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

    private static WiredStack stack(List<IWiredCondition> conditions) {
        return new WiredStack(null, null, conditions, List.of(), WiredExtraOrEval.MODE_ALL, 1, false, false, false);
    }

    private static WiredContext context(Room room, WiredServices services, WiredStack stack, WiredState state) {
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.CUSTOM, room).build();
        return new WiredContext(event, null, stack, services, state, null);
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
