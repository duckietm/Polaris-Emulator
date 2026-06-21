package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WiredEffectPayloadGuardTest {
    @Test
    void delayIsBoundedForStoredPayloads() {
        assertEquals(0, WiredEffectPayloadGuard.delay(-1));
        assertEquals(20, WiredEffectPayloadGuard.delay(20));
        assertEquals(WiredEffectPayloadGuard.MAX_LOAD_DELAY, WiredEffectPayloadGuard.delay(Integer.MAX_VALUE));
        assertEquals(0, WiredEffectPayloadGuard.parseDelay("bad"));
        assertEquals(5, WiredEffectPayloadGuard.parseDelay(" 5 "));
    }

    @Test
    void modeAndTextFallbacksAreSafe() {
        assertEquals(0, WiredEffectPayloadGuard.mode(-1));
        assertEquals(0, WiredEffectPayloadGuard.mode(2));
        assertEquals(1, WiredEffectPayloadGuard.mode(1));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredEffectPayloadGuard.furniSource(-1));
        assertEquals(WiredSourceUtil.SOURCE_SELECTED, WiredEffectPayloadGuard.furniSource(WiredSourceUtil.SOURCE_SELECTED));
        assertEquals("", WiredEffectPayloadGuard.text(null));
        assertEquals("botname", WiredEffectPayloadGuard.text("bot\tname"));
    }

    @Test
    void malformedJsonReturnsNullInsteadOfThrowing() {
        assertNull(WiredEffectPayloadGuard.fromJson("{broken", WiredEffectBotTalk.JsonData.class));
        assertNull(WiredEffectPayloadGuard.fromJson(null, WiredEffectBotTalk.JsonData.class));
    }
}
