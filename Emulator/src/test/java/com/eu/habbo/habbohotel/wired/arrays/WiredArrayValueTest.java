package com.eu.habbo.habbohotel.wired.arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class WiredArrayValueTest {

    @Test
    void listOperationsPreserveOrderAndSignedLongValues() {
        WiredArrayDefinition definition = recordDefinition("list", 6);
        WiredArrayValue value = WiredArrayValue.empty(definition, 12);

        assertEquals(
                WiredArrayMutationResult.SUCCESS,
                value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(Long.MIN_VALUE, 2)));
        assertEquals(
                WiredArrayMutationResult.SUCCESS,
                value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(Long.MAX_VALUE, 4)));
        assertEquals(
                WiredArrayMutationResult.SUCCESS, value.apply(WiredArrayStructuralOperation.INSERT, 1, 0, entry(7, 8)));
        assertEquals(List.of(Long.MIN_VALUE, 7L, Long.MAX_VALUE), firstFields(value));

        assertEquals(WiredArrayMutationResult.SUCCESS, value.apply(WiredArrayStructuralOperation.MOVE, 0, 2, null));
        assertEquals(List.of(7L, Long.MAX_VALUE, Long.MIN_VALUE), firstFields(value));
        assertEquals(WiredArrayMutationResult.SUCCESS, value.apply(WiredArrayStructuralOperation.SWAP, 0, 1, null));
        assertEquals(List.of(Long.MAX_VALUE, 7L, Long.MIN_VALUE), firstFields(value));
        assertEquals(
                WiredArrayMutationResult.SUCCESS, value.apply(WiredArrayStructuralOperation.REMOVE_FIRST, 0, 0, null));
        assertEquals(
                WiredArrayMutationResult.SUCCESS, value.apply(WiredArrayStructuralOperation.REMOVE_LAST, 0, 0, null));
        assertEquals(List.of(7L), firstFields(value));
    }

    @Test
    void failedMutationsAreAtomic() {
        WiredArrayDefinition definition = recordDefinition("list", 2);
        WiredArrayValue value = WiredArrayValue.empty(definition, 4);
        value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(1, 2));
        Map<Integer, WiredArrayEntry> before = value.entriesSnapshot();

        assertEquals(
                WiredArrayMutationResult.MISSING_FIELD,
                value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 3L)));
        assertEquals(
                WiredArrayMutationResult.INVALID_INDEX,
                value.apply(WiredArrayStructuralOperation.INSERT, 8, 0, entry(3, 4)));
        assertEquals(before, value.entriesSnapshot());
        assertEquals(1, value.getLogicalLength());
    }

    @Test
    void listReplacementRemovalClearAndCapacityResultsAreExplicit() {
        WiredArrayDefinition definition = recordDefinition("list", 2);
        WiredArrayValue value = WiredArrayValue.empty(definition, 4);
        value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(1, 2));
        value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(3, 4));

        assertEquals(
                WiredArrayMutationResult.ARRAY_FULL,
                value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(5, 6)));
        assertEquals(
                WiredArrayMutationResult.SUCCESS,
                value.apply(WiredArrayStructuralOperation.SET_ENTRY, 0, 0, entry(7, 8)));
        assertEquals(7L, value.getEntry(0).getValue(1));
        assertEquals(WiredArrayMutationResult.SUCCESS, value.apply(WiredArrayStructuralOperation.REMOVE, 0, 0, null));
        assertEquals(3L, value.getEntry(0).getValue(1));
        assertEquals(WiredArrayMutationResult.SUCCESS, value.apply(WiredArrayStructuralOperation.CLEAR, 0, 0, null));
        assertEquals(WiredArrayMutationResult.NO_CHANGE, value.apply(WiredArrayStructuralOperation.CLEAR, 0, 0, null));
        assertEquals(
                WiredArrayMutationResult.ARRAY_EMPTY,
                value.apply(WiredArrayStructuralOperation.REMOVE_FIRST, 0, 0, null));
    }

    @Test
    void slotArraysRemainSparseAndBounded() {
        WiredArrayDefinition definition = recordDefinition("slots", 4);
        WiredArrayValue value = WiredArrayValue.empty(definition, 4);

        assertEquals(
                WiredArrayMutationResult.SUCCESS,
                value.apply(WiredArrayStructuralOperation.SET_ENTRY, 3, 0, entry(9, 10)));
        assertEquals(1, value.getOccupiedCount());
        assertEquals(3, value.getAvailableIndexes());
        assertNull(value.getEntry(0));
        assertEquals(9L, value.getEntry(3).getValue(1));
        assertEquals(WiredArrayMutationResult.SUCCESS, value.apply(WiredArrayStructuralOperation.SWAP, 3, 0, null));
        assertEquals(9L, value.getEntry(0).getValue(1));
        assertNull(value.getEntry(3));
        assertEquals(
                WiredArrayMutationResult.SUCCESS, value.apply(WiredArrayStructuralOperation.CLEAR_SLOT, 0, 0, null));
        assertEquals(
                WiredArrayMutationResult.EMPTY_SLOT, value.apply(WiredArrayStructuralOperation.CLEAR_SLOT, 0, 0, null));
    }

    @Test
    void slotReplacementAndEmptySwapDoNotConsumeExtraCapacity() {
        WiredArrayDefinition definition = recordDefinition("slots", 2);
        WiredArrayValue value = WiredArrayValue.empty(definition, 2);

        assertEquals(WiredArrayMutationResult.NO_CHANGE, value.apply(WiredArrayStructuralOperation.SWAP, 0, 1, null));
        assertEquals(
                WiredArrayMutationResult.SUCCESS,
                value.apply(WiredArrayStructuralOperation.SET_ENTRY, 1, 0, entry(1, 2)));
        assertEquals(
                WiredArrayMutationResult.SUCCESS,
                value.apply(WiredArrayStructuralOperation.SET_ENTRY, 1, 0, entry(3, 4)));
        assertEquals(1, value.getOccupiedCount());
        assertEquals(3L, value.getEntry(1).getValue(1));
        assertEquals(
                WiredArrayMutationResult.INVALID_INDEX,
                value.apply(WiredArrayStructuralOperation.SET_ENTRY, 2, 0, entry(5, 6)));
    }

    @Test
    void populatedCellLimitStopsWideArraysBeforeCapacity() {
        WiredArrayDefinition definition = recordDefinition("list", 5);
        WiredArrayValue value = WiredArrayValue.empty(definition, 2);

        assertEquals(
                WiredArrayMutationResult.SUCCESS, value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(1, 2)));
        assertEquals(
                WiredArrayMutationResult.POPULATED_CELL_LIMIT,
                value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(3, 4)));
        assertEquals(1, value.getLogicalLength());
    }

    @Test
    void fieldMutationsRequireAnOwnerEntryExceptForAssignment() {
        WiredArrayValue value = WiredArrayValue.empty(recordDefinition("list", 3), 6);

        assertEquals(
                WiredArrayMutationResult.MISSING_ENTRY,
                value.mutateField(0, 1, WiredArrayNumericOperation.ADD, 5).result());

        WiredArrayValue.FieldMutation created =
                value.mutateField(0, 1, WiredArrayNumericOperation.ASSIGN, Long.MIN_VALUE);
        assertEquals(WiredArrayMutationResult.SUCCESS, created.result());
        assertEquals(Long.MIN_VALUE, created.currentValue());
        assertEquals(1, value.getLogicalLength());

        WiredArrayValue.FieldMutation bitCount = value.mutateField(0, 1, WiredArrayNumericOperation.BIT_COUNT, 0);
        assertEquals(1L, bitCount.currentValue());
        assertEquals(
                WiredArrayMutationResult.MISSING_ENTRY,
                value.mutateField(2, 1, WiredArrayNumericOperation.ASSIGN, 1).result());
    }

    @Test
    void numericMinAndMaxUseTheirAdvertisedDirection() {
        assertEquals(3L, WiredArrayNumericOperation.MIN.apply(9, 3));
        assertEquals(9L, WiredArrayNumericOperation.MAX.apply(9, 3));
    }

    @Test
    void arrayChangeCodesRetainStructuralAndLengthFacts() {
        WiredArrayChange appended = WiredArrayChange.structural(WiredArrayStructuralOperation.APPEND, 0, 0, 2, 3);
        assertEquals(WiredArrayChangeType.ENTRY_APPENDED, appended.changeType());
        assertEquals(2, appended.index());
        assertEquals(2, appended.oldLength());
        assertEquals(3, appended.newLength());

        WiredArrayChange moved = WiredArrayChange.structural(WiredArrayStructuralOperation.MOVE, 4, 1, 5, 5);
        assertEquals(WiredArrayChangeType.ENTRY_MOVED, moved.changeType());
        assertEquals(4, moved.sourceIndex());
        assertEquals(1, moved.destinationIndex());
    }

    @Test
    void shuffleChangesOrderWithoutChangingEntries() {
        WiredArrayDefinition definition = recordDefinition("list", 4);
        WiredArrayValue value = WiredArrayValue.empty(definition, 8);
        value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(1, 10));
        value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(2, 20));
        value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(3, 30));
        List<Long> before = firstFields(value);

        RandomGenerator shuffledOrder = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
        assertEquals(
                WiredArrayMutationResult.SUCCESS,
                value.apply(WiredArrayStructuralOperation.SHUFFLE, 0, 0, null, shuffledOrder));

        List<Long> after = firstFields(value);
        assertNotEquals(before, after);
        assertEquals(before.stream().sorted().toList(), after.stream().sorted().toList());
    }

    @Test
    void shuffleMayHonestlyReportNoChangeForAnIdenticalPermutation() {
        WiredArrayDefinition definition = recordDefinition("list", 2);
        WiredArrayValue value = WiredArrayValue.empty(definition, 4);
        value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(1, 10));
        value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, entry(2, 20));
        RandomGenerator identity = new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        };

        assertEquals(
                WiredArrayMutationResult.NO_CHANGE,
                value.apply(WiredArrayStructuralOperation.SHUFFLE, 0, 0, null, identity));
        assertEquals(List.of(1L, 2L), firstFields(value));
    }

    private static WiredArrayDefinition recordDefinition(String mode, int maximum) {
        WiredVariableDefinitionData data = WiredArrayDefinitionTest.arrayData("record", mode, maximum);
        data.fields =
                List.of(new WiredArrayFieldDefinition(1, "ItemID", 0), new WiredArrayFieldDefinition(2, "Quantity", 1));
        data.nextFieldId = 3;
        return WiredArrayDefinition.fromData(data, maximum);
    }

    private static Map<Integer, Long> entry(long first, long second) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        result.put(1, first);
        result.put(2, second);
        return result;
    }

    private static List<Long> firstFields(WiredArrayValue value) {
        return value.entriesSnapshot().values().stream()
                .map(entry -> entry.getValue(1))
                .toList();
    }
}
