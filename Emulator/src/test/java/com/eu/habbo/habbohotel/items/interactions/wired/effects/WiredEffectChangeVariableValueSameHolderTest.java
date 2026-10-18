package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.REF_CONSTANT;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.REF_VARIABLE;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.TARGET_CONTEXT;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.TARGET_FURNI;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.TARGET_ROOM;
import static com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectChangeVariableValue.TARGET_USER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** "Use the same furni or user as the target variable" on Change Variable. */
class WiredEffectChangeVariableValueSameHolderTest {

    @Test
    void appliesToAVariableReferenceOnTheDestinationsOwnUserOrFurni() {
        assertTrue(WiredEffectChangeVariableValue.sameHolderApplies(true, REF_VARIABLE, TARGET_USER, TARGET_USER));
        assertTrue(WiredEffectChangeVariableValue.sameHolderApplies(true, REF_VARIABLE, TARGET_FURNI, TARGET_FURNI));
    }

    @Test
    void isDroppedWhereItMeansNothing() {
        assertFalse(WiredEffectChangeVariableValue.sameHolderApplies(false, REF_VARIABLE, TARGET_USER, TARGET_USER));
        assertFalse(WiredEffectChangeVariableValue.sameHolderApplies(true, REF_CONSTANT, TARGET_USER, TARGET_USER));
        assertFalse(WiredEffectChangeVariableValue.sameHolderApplies(true, REF_VARIABLE, TARGET_USER, TARGET_FURNI));
        assertFalse(WiredEffectChangeVariableValue.sameHolderApplies(true, REF_VARIABLE, TARGET_ROOM, TARGET_ROOM));
        assertFalse(
                WiredEffectChangeVariableValue.sameHolderApplies(true, REF_VARIABLE, TARGET_CONTEXT, TARGET_CONTEXT));
    }
}
