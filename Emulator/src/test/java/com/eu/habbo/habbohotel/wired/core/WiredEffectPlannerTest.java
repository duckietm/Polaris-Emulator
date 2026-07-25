package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.api.IWiredCondition;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredEffectPlannerTest {

    @Test
    void preservesPositiveNegativeAndSelectorEligibility() {
        WiredEffectPlanner planner = new WiredEffectPlanner();
        IWiredEffect regular = new TestEffect(0, false, false, false);
        IWiredEffect selector = new TestEffect(0, false, true, false);
        InteractionWiredEffect negative = interactionEffect(WiredEffectType.NEG_LOG);
        InteractionWiredEffect positive = interactionEffect(WiredEffectType.SHOW_MESSAGE);
        IWiredCondition condition = context -> true;
        WiredStack withCondition =
                new WiredStack(null, null, List.of(condition), List.of(selector, negative, regular, positive));
        WiredStack withoutCondition =
                new WiredStack(null, null, List.of(), List.of(selector, negative, regular, positive));

        assertEquals(List.of(regular, positive), planner.executableEffects(withCondition, true));
        assertEquals(List.of(negative), planner.executableEffects(withCondition, false));
        assertEquals(List.of(), planner.executableEffects(withoutCondition, false));
    }

    @Test
    void preservesProducerBeforeFilterSelectorOrder() {
        WiredEffectPlanner planner = new WiredEffectPlanner();
        IWiredEffect regular = new TestEffect(0, false, false, false);
        IWiredEffect producerOne = new TestEffect(0, false, true, false);
        IWiredEffect filter = new TestEffect(0, false, true, true);
        IWiredEffect producerTwo = new TestEffect(0, false, true, false);

        WiredEffectPlanner.SelectorPlan plan = planner.selectorPlan(List.of(filter, regular, producerOne, producerTwo));

        assertEquals(List.of(producerOne, producerTwo), plan.immediate());
        assertEquals(List.of(filter), plan.deferred());
        assertThrows(UnsupportedOperationException.class, () -> plan.immediate().add(filter));
    }

    @Test
    void preservesFirstSeenDelayGroupsAndActorFiltering() {
        WiredEffectPlanner planner = new WiredEffectPlanner();
        IWiredEffect delayedTwoFirst = new TestEffect(2, false, false, false);
        IWiredEffect immediate = new TestEffect(0, false, false, false);
        IWiredEffect delayedTwoSecond = new TestEffect(2, false, false, false);
        IWiredEffect actorOnly = new TestEffect(1, true, false, false);
        List<IWiredEffect> effects = List.of(delayedTwoFirst, immediate, delayedTwoSecond, actorOnly);

        List<WiredEffectPlanner.DelayBatch> withoutActor = planner.orderedDelayBatches(effects, false);
        assertEquals(2, withoutActor.size());
        assertEquals(2, withoutActor.get(0).delay());
        assertEquals(
                List.of(delayedTwoFirst, delayedTwoSecond), withoutActor.get(0).effects());
        assertEquals(0, withoutActor.get(1).delay());
        assertEquals(List.of(immediate), withoutActor.get(1).effects());

        List<WiredEffectPlanner.DelayBatch> withActor = planner.orderedDelayBatches(effects, true);
        assertEquals(
                List.of(2, 0, 1),
                withActor.stream().map(WiredEffectPlanner.DelayBatch::delay).toList());
        assertEquals(List.of(actorOnly), withActor.get(2).effects());
        assertThrows(
                UnsupportedOperationException.class,
                () -> withActor.get(0).effects().add(actorOnly));
    }

    private static InteractionWiredEffect interactionEffect(WiredEffectType type) {
        InteractionWiredEffect effect = mock(InteractionWiredEffect.class);
        when(effect.getType()).thenReturn(type);
        return effect;
    }

    private record TestEffect(int delay, boolean actorRequired, boolean selector, boolean filter)
            implements IWiredEffect {

        @Override
        public void execute(WiredContext context) {}

        @Override
        public int getDelay() {
            return this.delay;
        }

        @Override
        public boolean requiresActor() {
            return this.actorRequired;
        }

        @Override
        public boolean isSelector() {
            return this.selector;
        }

        @Override
        public boolean usesExistingSelectorTargets() {
            return this.filter;
        }
    }
}
