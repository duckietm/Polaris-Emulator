package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WiredMovementPayloadGuardTest {
    @Test
    void delayAndIntegersAreBounded() {
        assertEquals(0, WiredMovementPayloadGuard.delay(-100));
        assertEquals(25, WiredMovementPayloadGuard.parseDelay("25"));
        assertEquals(0, WiredMovementPayloadGuard.parseDelay("bad"));
        assertEquals(WiredMovementPayloadGuard.MAX_LOAD_DELAY, WiredMovementPayloadGuard.delay(Integer.MAX_VALUE));
        assertEquals(7, WiredMovementPayloadGuard.parseInt(" 7 ", 0));
        assertEquals(3, WiredMovementPayloadGuard.parseInt("oops", 3));
    }

    @Test
    void sourceValuesFallbackToTriggerWhenInvalid() {
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredMovementPayloadGuard.furniSource(-1));
        assertEquals(WiredSourceUtil.SOURCE_SELECTED, WiredMovementPayloadGuard.furniSource(WiredSourceUtil.SOURCE_SELECTED));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredMovementPayloadGuard.userSource(999));
        assertEquals(WiredSourceUtil.SOURCE_SELECTOR, WiredMovementPayloadGuard.userSource(WiredSourceUtil.SOURCE_SELECTOR));
    }

    @Test
    void malformedJsonDoesNotThrow() {
        assertNull(WiredMovementPayloadGuard.fromJson("{broken", WiredEffectTeleport.JsonData.class));
        assertNull(WiredMovementPayloadGuard.fromJson(null, WiredEffectTeleport.JsonData.class));
    }
}
