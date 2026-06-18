package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WiredUtilityPayloadGuardTest {
    @Test
    void clampsDelayAndParsesLegacyNumbers() {
        assertEquals(0, WiredUtilityPayloadGuard.delay(-1));
        assertEquals(20, WiredUtilityPayloadGuard.parseDelay("20"));
        assertEquals(0, WiredUtilityPayloadGuard.parseDelay("bad"));
        assertEquals(WiredUtilityPayloadGuard.MAX_LOAD_DELAY, WiredUtilityPayloadGuard.delay(Integer.MAX_VALUE));
    }

    @Test
    void normalizesSourcesAndText() {
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredUtilityPayloadGuard.userSource(999));
        assertEquals(WiredSourceUtil.SOURCE_SELECTOR, WiredUtilityPayloadGuard.userSource(WiredSourceUtil.SOURCE_SELECTOR));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredUtilityPayloadGuard.furniSource(-1));
        assertEquals(WiredSourceUtil.SOURCE_SELECTED, WiredUtilityPayloadGuard.furniSource(WiredSourceUtil.SOURCE_SELECTED));
        assertEquals("", WiredUtilityPayloadGuard.text(null));
    }

    @Test
    void malformedJsonReturnsNull() {
        assertNull(WiredUtilityPayloadGuard.fromJson("{broken", WiredEffectResetTimers.JsonData.class));
        assertNull(WiredUtilityPayloadGuard.fromJson(null, WiredEffectResetTimers.JsonData.class));
    }
}
