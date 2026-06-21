package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class WiredExtraPayloadGuardTest {
    @Test
    void malformedJsonReturnsNull() {
        assertNull(WiredExtraPayloadGuard.fromJson("{broken", WiredExtraAnimationTime.JsonData.class));
        assertNull(WiredExtraPayloadGuard.fromJson(null, WiredExtraAnimationTime.JsonData.class));
    }
}
