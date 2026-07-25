package com.eu.habbo.habbohotel.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WiredHandlerLegacyUsageTelemetryTest {
    private static Field configField;
    private static Object originalConfig;

    @BeforeAll
    static void provideLegacyStaticConfiguration() throws ReflectiveOperationException {
        configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        originalConfig = configField.get(null);
        configField.set(null, mock(ConfigurationManager.class));
    }

    @AfterAll
    static void restoreConfiguration() throws ReflectiveOperationException {
        configField.set(null, originalConfig);
    }

    @BeforeEach
    @AfterEach
    void resetTelemetry() {
        WiredLegacyUsageTelemetry.resetForTests();
    }

    @Test
    void harmlessRejectedLegacyEntryPointIsStillCounted() {
        assertFalse(WiredHandler.handle((WiredTriggerType) null, null, null, null));
        assertEquals(1, WiredLegacyUsageTelemetry.count(WiredLegacyUsageTelemetry.Operation.HANDLE_TRIGGER_TYPE));
    }

    @Test
    void tileExecutionEntryPointIsCountedWithoutRetainingRoomState() {
        assertTrue(WiredHandler.executeEffectsAtTiles(List.of(), null, null, new Object[0]));
        assertEquals(1, WiredLegacyUsageTelemetry.count(WiredLegacyUsageTelemetry.Operation.EXECUTE_EFFECTS_AT_TILES));
    }

    @Test
    void countersRemainIndependentAndUseLongRange() {
        WiredLegacyUsageTelemetry.record(WiredLegacyUsageTelemetry.Operation.RESET_TIMERS, null);
        WiredLegacyUsageTelemetry.record(WiredLegacyUsageTelemetry.Operation.RESET_TIMERS, null);
        WiredLegacyUsageTelemetry.record(WiredLegacyUsageTelemetry.Operation.GET_REWARD, null);

        assertEquals(2, WiredLegacyUsageTelemetry.count(WiredLegacyUsageTelemetry.Operation.RESET_TIMERS));
        assertEquals(1, WiredLegacyUsageTelemetry.count(WiredLegacyUsageTelemetry.Operation.GET_REWARD));
        assertEquals(0, WiredLegacyUsageTelemetry.count(WiredLegacyUsageTelemetry.Operation.DROP_REWARDS));
    }
}
