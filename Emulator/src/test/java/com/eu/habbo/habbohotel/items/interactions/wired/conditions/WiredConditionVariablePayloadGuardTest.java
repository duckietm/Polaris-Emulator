package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredConditionVariablePayloadGuardTest {
    @Test
    void variableAgeDurationsAreBounded() {
        assertEquals(0, WiredConditionVariableAgeMatch.normalizeDurationAmount(-1));
        assertEquals(42, WiredConditionVariableAgeMatch.normalizeDurationAmount(42));
        assertEquals(WiredConditionVariableAgeMatch.MAX_DURATION_AMOUNT, WiredConditionVariableAgeMatch.normalizeDurationAmount(Integer.MAX_VALUE));
        assertEquals(0L, WiredConditionVariableAgeMatch.durationToMillis(-1, 1));
        assertEquals(1_000L, WiredConditionVariableAgeMatch.durationToMillis(1, 1));
    }

    @Test
    void variableAgeModesFallBackToSafeDefaults() {
        assertEquals(0, WiredConditionVariableAgeMatch.normalizeTargetTypeExtended(999));
        assertEquals(1, WiredConditionVariableAgeMatch.normalizeTargetTypeExtended(1));
        assertEquals(0, WiredConditionVariableAgeMatch.normalizeCompareValue(99));
        assertEquals(1, WiredConditionVariableAgeMatch.normalizeCompareValue(1));
        assertEquals(0, WiredConditionVariableAgeMatch.normalizeComparison(99));
        assertEquals(2, WiredConditionVariableAgeMatch.normalizeComparison(2));
        assertEquals(1, WiredConditionVariableAgeMatch.normalizeDurationUnit(99));
        assertEquals(7, WiredConditionVariableAgeMatch.normalizeDurationUnit(7));
    }

    @Test
    void variableValueModesAndConstantsAreBounded() {
        assertEquals(0, WiredConditionVariableValueMatch.normalizeTargetTypeExtended(900));
        assertEquals(3, WiredConditionVariableValueMatch.normalizeTargetTypeExtended(3));
        assertEquals(0, WiredConditionVariableValueMatch.normalizeReferenceMode(5));
        assertEquals(1, WiredConditionVariableValueMatch.normalizeReferenceMode(1));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredConditionVariableValueMatch.normalizeReferenceFurniSource(-1));
        assertEquals(101, WiredConditionVariableValueMatch.normalizeReferenceFurniSource(101));
        assertEquals(2, WiredConditionVariableValueMatch.normalizeComparison(99));
        assertEquals(5, WiredConditionVariableValueMatch.normalizeComparison(5));
        assertEquals(WiredConditionVariableValueMatch.MAX_ABS_REFERENCE_CONSTANT, WiredConditionVariableValueMatch.normalizeReferenceConstantValue(Integer.MAX_VALUE));
        assertEquals(-WiredConditionVariableValueMatch.MAX_ABS_REFERENCE_CONSTANT, WiredConditionVariableValueMatch.normalizeReferenceConstantValue(Integer.MIN_VALUE));
        assertEquals(0, WiredConditionVariableValueMatch.parseInteger("nope"));
        assertEquals(123, WiredConditionVariableValueMatch.parseInteger(" 123 "));
    }
}
