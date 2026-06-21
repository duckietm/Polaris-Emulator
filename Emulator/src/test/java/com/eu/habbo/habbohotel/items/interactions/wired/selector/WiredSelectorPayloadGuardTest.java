package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class WiredSelectorPayloadGuardTest {
    @Test
    void malformedJsonReturnsNull() {
        assertNull(WiredSelectorPayloadGuard.fromJson("{broken", WiredEffectFurniArea.JsonData.class));
        assertNull(WiredSelectorPayloadGuard.fromJson(null, WiredEffectFurniArea.JsonData.class));
    }
}
