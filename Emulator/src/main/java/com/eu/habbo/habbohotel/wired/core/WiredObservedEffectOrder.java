package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectControlClock;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveScore;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveScoreToTeam;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMatchFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRelativeMove;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectToggleFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectWhisper;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The order Habbo runs a firing's actions in without Execute in Order, where it differs from the
 * stack's height order: Match to Snapshot runs after Toggle State and Relative Move, and Show
 * Message after Change Variable and Toggle State, and Give Score (To Team) after Control Clock, so a
 * score lands in the game the clock starts, whichever is placed higher. Only actions with the same
 * delay are compared. Everything else keeps the stack order.
 */
final class WiredObservedEffectOrder {

    private WiredObservedEffectOrder() {}

    static List<IWiredEffect> apply(List<IWiredEffect> effects) {
        if (effects == null || effects.size() < 2) {
            return effects;
        }

        Map<Integer, List<Integer>> slotsByDelay = new LinkedHashMap<>();
        for (int index = 0; index < effects.size(); index++) {
            IWiredEffect effect = effects.get(index);
            int delay = effect == null ? 0 : effect.getDelay();
            slotsByDelay.computeIfAbsent(delay, ignored -> new ArrayList<>()).add(index);
        }

        List<IWiredEffect> ordered = new ArrayList<>(effects);

        for (List<Integer> slots : slotsByDelay.values()) {
            if (slots.size() < 2) {
                continue;
            }

            List<IWiredEffect> group = new ArrayList<>(slots.size());
            for (int slot : slots) {
                group.add(effects.get(slot));
            }

            moveBehind(group, WiredEffectMatchFurni.class::isInstance, WiredObservedEffectOrder::changesFurni);
            moveBehind(group, WiredEffectWhisper.class::isInstance, WiredObservedEffectOrder::changesWhatAMessageShows);
            moveBehind(group, WiredObservedEffectOrder::givesScore, WiredEffectControlClock.class::isInstance);

            for (int position = 0; position < slots.size(); position++) {
                ordered.set(slots.get(position), group.get(position));
            }
        }

        return ordered;
    }

    private static boolean givesScore(IWiredEffect effect) {
        return effect instanceof WiredEffectGiveScore || effect instanceof WiredEffectGiveScoreToTeam;
    }

    private static boolean changesFurni(IWiredEffect effect) {
        return effect instanceof WiredEffectToggleFurni || effect instanceof WiredEffectRelativeMove;
    }

    private static boolean changesWhatAMessageShows(IWiredEffect effect) {
        return effect instanceof WiredEffectChangeVariableValue || effect instanceof WiredEffectToggleFurni;
    }

    /** Moves each mover that sits before a prerequisite to just behind the last prerequisite. */
    private static void moveBehind(
            List<IWiredEffect> group, Predicate<IWiredEffect> mover, Predicate<IWiredEffect> prerequisite) {
        List<IWiredEffect> movers = new ArrayList<>();

        for (int index = 0; index < group.size(); index++) {
            IWiredEffect effect = group.get(index);

            if (mover.test(effect) && hasLater(group, index, prerequisite)) {
                movers.add(effect);
            }
        }

        if (movers.isEmpty()) {
            return;
        }

        group.removeAll(movers);

        int lastPrerequisite = -1;
        for (int index = 0; index < group.size(); index++) {
            if (prerequisite.test(group.get(index))) {
                lastPrerequisite = index;
            }
        }

        group.addAll(lastPrerequisite + 1, movers);
    }

    private static boolean hasLater(List<IWiredEffect> group, int index, Predicate<IWiredEffect> prerequisite) {
        for (int later = index + 1; later < group.size(); later++) {
            if (prerequisite.test(group.get(later))) {
                return true;
            }
        }

        return false;
    }
}
