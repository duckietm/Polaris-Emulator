package polaris.hotel.wired.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.items.interactions.wired.effects.WiredEffectNegativeLog;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.api.IWiredEffect;
import polaris.hotel.wired.api.WiredStack;

/**
 * Pure internal planner for effect eligibility, selector order and ordered delay groups.
 *
 * <p>Execution, cooldowns, random/unseen state, delayed scheduling and plugin callbacks remain in
 * the engine. Plans retain the existing physical list order and contain the same effect instances.
 */
final class WiredEffectPlanner {

    List<IWiredEffect> executableEffects(WiredStack stack, boolean conditionsPassed) {
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

    SelectorPlan selectorPlan(List<IWiredEffect> effects) {
        if (effects.isEmpty()) {
            return SelectorPlan.EMPTY;
        }

        List<IWiredEffect> immediateSelectors = new ArrayList<>();
        List<IWiredEffect> deferredSelectors = new ArrayList<>();

        for (IWiredEffect effect : effects) {
            if (!effect.isSelector()) {
                continue;
            }

            if (effect.usesExistingSelectorTargets()) {
                deferredSelectors.add(effect);
            } else {
                immediateSelectors.add(effect);
            }
        }

        return new SelectorPlan(List.copyOf(immediateSelectors), List.copyOf(deferredSelectors));
    }

    List<DelayBatch> orderedDelayBatches(List<IWiredEffect> effects, boolean hasActor) {
        if (effects == null || effects.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<IWiredEffect>> effectsByDelay = new LinkedHashMap<>();

        for (IWiredEffect effect : effects) {
            if (effect == null) {
                continue;
            }

            if (effect.requiresActor() && !hasActor) {
                continue;
            }

            effectsByDelay
                    .computeIfAbsent(effect.getDelay(), key -> new ArrayList<>())
                    .add(effect);
        }

        List<DelayBatch> batches = new ArrayList<>(effectsByDelay.size());
        for (Map.Entry<Integer, List<IWiredEffect>> entry : effectsByDelay.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                batches.add(new DelayBatch(entry.getKey(), List.copyOf(entry.getValue())));
            }
        }
        return List.copyOf(batches);
    }

    private static boolean isNegativeConditionEffect(IWiredEffect effect) {
        if (!(effect instanceof InteractionWiredEffect interactionEffect)) {
            return false;
        }

        // The negative log shares its type with the positive log (the type code only picks the client
        // dialog), so the class is the only thing that says which branch it belongs to.
        if (interactionEffect instanceof WiredEffectNegativeLog) {
            return true;
        }

        WiredEffectType effectType = interactionEffect.getType();
        return effectType == WiredEffectType.NEG_CALL_STACKS
                || effectType == WiredEffectType.NEG_SEND_SIGNAL
                || effectType == WiredEffectType.NEG_SHOW_MESSAGE;
    }

    record SelectorPlan(List<IWiredEffect> immediate, List<IWiredEffect> deferred) {
        private static final SelectorPlan EMPTY = new SelectorPlan(List.of(), List.of());
    }

    record DelayBatch(int delay, List<IWiredEffect> effects) {}
}
