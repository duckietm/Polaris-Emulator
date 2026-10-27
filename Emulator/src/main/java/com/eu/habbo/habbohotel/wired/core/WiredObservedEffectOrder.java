package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectAdjustClock;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectControlClock;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectFurniToUser;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveScore;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveScoreToTeam;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMatchFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectMoveRotateFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRelativeMove;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectRemoveVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectToggleFurni;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectUserToFurni;
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
 * score lands in the game the clock starts, whichever is placed higher, and an Adjust Clock that sets the clock
 * after the ones that increase or decrease it, Remove Variable after Give Variable, Give and Change Variable after the
 * furni and user moves, and User To Furni after Toggle State and Move/Rotate Furni (observed on AIR
 * WIN63-202610201032). Only actions with the same
 * delay are compared. Everything else keeps the stack order.
 */
final class WiredObservedEffectOrder {

    private record Rule(Predicate<IWiredEffect> mover, Predicate<IWiredEffect> prerequisite) {}

    /** Each mover runs behind the last of its prerequisites. Observed on AIR WIN63-202610201032, 12 of 12 each. */
    private static final List<Rule> RULES = List.of(
            new Rule(WiredEffectMatchFurni.class::isInstance, WiredObservedEffectOrder::changesFurni),
            new Rule(WiredEffectWhisper.class::isInstance, WiredObservedEffectOrder::changesWhatAMessageShows),
            new Rule(WiredObservedEffectOrder::givesScore, WiredEffectControlClock.class::isInstance),
            new Rule(WiredObservedEffectOrder::setsClock, WiredObservedEffectOrder::stepsClock),
            // A variable removed in the firing that gives it is gone afterwards.
            new Rule(WiredEffectRemoveVariable.class::isInstance, WiredEffectGiveVariable.class::isInstance),
            // Give and Change Variable read the state, rotation and position the furni and user moves leave.
            new Rule(WiredObservedEffectOrder::writesVariable, WiredObservedEffectOrder::movesOrTurns),
            // User To Furni walks onto the furni where Toggle State and Move/Rotate Furni put it.
            new Rule(WiredEffectUserToFurni.class::isInstance, WiredObservedEffectOrder::togglesOrMovesFurni));

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

            // One move can undo a pair settled before it (Remove behind Give, Give behind Toggle), so the rules
            // run until nothing moves, at most once per action.
            for (int pass = 0; pass < group.size(); pass++) {
                boolean moved = false;
                for (Rule rule : RULES) {
                    moved |= moveBehind(group, rule.mover(), rule.prerequisite());
                }
                if (!moved) break;
            }

            for (int position = 0; position < slots.size(); position++) {
                ordered.set(slots.get(position), group.get(position));
            }
        }

        return ordered;
    }

    private static boolean setsClock(IWiredEffect effect) {
        return effect instanceof WiredEffectAdjustClock adjust && adjust.isSet();
    }

    private static boolean stepsClock(IWiredEffect effect) {
        return effect instanceof WiredEffectAdjustClock adjust && !adjust.isSet();
    }

    private static boolean givesScore(IWiredEffect effect) {
        return effect instanceof WiredEffectGiveScore || effect instanceof WiredEffectGiveScoreToTeam;
    }

    private static boolean changesFurni(IWiredEffect effect) {
        return effect instanceof WiredEffectToggleFurni || effect instanceof WiredEffectRelativeMove;
    }

    private static boolean changesWhatAMessageShows(IWiredEffect effect) {
        return effect instanceof WiredEffectChangeVariableValue
                || effect instanceof WiredEffectGiveVariable
                || effect instanceof WiredEffectRemoveVariable
                || effect instanceof WiredEffectToggleFurni;
    }

    private static boolean writesVariable(IWiredEffect effect) {
        return effect instanceof WiredEffectGiveVariable || effect instanceof WiredEffectChangeVariableValue;
    }

    private static boolean togglesOrMovesFurni(IWiredEffect effect) {
        return effect instanceof WiredEffectToggleFurni || effect instanceof WiredEffectMoveRotateFurni;
    }

    private static boolean movesOrTurns(IWiredEffect effect) {
        return togglesOrMovesFurni(effect)
                || effect instanceof WiredEffectUserToFurni
                || effect instanceof WiredEffectFurniToUser;
    }

    /** Moves each mover that sits before a prerequisite to just behind the last prerequisite; true when one moved. */
    private static boolean moveBehind(
            List<IWiredEffect> group, Predicate<IWiredEffect> mover, Predicate<IWiredEffect> prerequisite) {
        List<IWiredEffect> movers = new ArrayList<>();

        for (int index = 0; index < group.size(); index++) {
            IWiredEffect effect = group.get(index);

            if (mover.test(effect) && hasLater(group, index, prerequisite)) {
                movers.add(effect);
            }
        }

        if (movers.isEmpty()) {
            return false;
        }

        group.removeAll(movers);

        int lastPrerequisite = -1;
        for (int index = 0; index < group.size(); index++) {
            if (prerequisite.test(group.get(index))) {
                lastPrerequisite = index;
            }
        }

        group.addAll(lastPrerequisite + 1, movers);
        return true;
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
