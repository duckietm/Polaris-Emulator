package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.wired.arrays.WiredArrayCaptureSnapshot;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayEntry;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayFieldDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayMutationResult;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayStructuralOperation;
import com.eu.habbo.habbohotel.wired.arrays.WiredVariableDefinitionData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WiredContextVariableScopeArrayTest {

    @Test
    void readsAndMutationsDoNotImplicitlyCreateAnArray() {
        WiredArrayDefinition definition = definition();
        WiredContextVariableScope scope = new WiredContextVariableScope();

        assertNull(scope.getArrayValue(77, definition));
        assertFalse(scope.hasArray(77));
        assertEquals(
                WiredArrayMutationResult.MISSING_OWNER,
                scope.mutateArray(77, definition, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 1L)));

        assertEquals(WiredArrayMutationResult.SUCCESS, scope.giveArray(77, definition, false));
        assertTrue(scope.hasArray(77));
        assertTrue(scope.removeArray(77));
        assertFalse(scope.hasArray(77));
    }

    @Test
    void forksCopyArraysAndCapturesWithoutSharingMutations() {
        WiredArrayDefinition definition = definition();
        WiredContextVariableScope original = new WiredContextVariableScope();
        assertEquals(WiredArrayMutationResult.SUCCESS, original.giveArray(77, definition, false));
        assertEquals(
                WiredArrayMutationResult.SUCCESS,
                original.mutateArray(77, definition, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 12L)));
        original.publishArrayCapture(
                "Inventory",
                WiredArrayCaptureSnapshot.found(
                        definition, 0, 1, WiredArrayEntry.fromValues(definition, Map.of(1, 12L))));

        WiredContextVariableScope fork = original.copy();
        assertEquals(
                WiredArrayMutationResult.SUCCESS,
                fork.mutateArray(77, definition, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 99L)));
        fork.publishArrayCapture("Inventory", WiredArrayCaptureSnapshot.missing(2));

        assertEquals(1, original.getArrayValue(77, definition).getLogicalLength());
        assertEquals(2, fork.getArrayValue(77, definition).getLogicalLength());
        assertEquals(12L, original.readArrayCapture("@array.inventory.value"));
        assertEquals(12L, original.readArrayCapture("inventory.value"));
        assertEquals(1L, original.readArrayCapture("@array.inventory.found"));
        assertEquals(0L, fork.readArrayCapture("@array.inventory.found"));
        assertNull(original.readArrayCapture("@array.inventory.unknown"));
        assertTrue(WiredInternalVariableSupport.canUseContextReference("inventory.value"));
        assertTrue(WiredInternalVariableSupport.canUseContextReference("@array.inventory.found"));
    }

    @Test
    void publishedReadViewStaysStableAcrossCopyOnWriteMutation() {
        WiredArrayDefinition definition = definition();
        WiredContextVariableScope scope = new WiredContextVariableScope();
        assertEquals(WiredArrayMutationResult.SUCCESS, scope.giveArray(77, definition, false));
        assertEquals(
                WiredArrayMutationResult.SUCCESS,
                scope.mutateArray(77, definition, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 12L)));
        var published = scope.getArrayView(77, definition);

        assertEquals(
                WiredArrayMutationResult.SUCCESS,
                scope.mutateArray(77, definition, WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, 99L)));

        assertEquals(1, published.getLogicalLength());
        assertEquals(2, scope.getArrayView(77, definition).getLogicalLength());
    }

    private static WiredArrayDefinition definition() {
        WiredVariableDefinitionData data = new WiredVariableDefinitionData();
        data.valueShape = "array";
        data.arrayFormat = "record";
        data.arrayMode = "list";
        data.maxEntries = 4;
        data.nextFieldId = 2;
        data.schemaVersion = 1;
        data.fields = List.of(new WiredArrayFieldDefinition(1, "Value", 0));
        return WiredArrayDefinition.fromData(data, 4);
    }
}
