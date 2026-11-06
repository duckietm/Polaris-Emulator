package polaris.hotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WiredExtraPayloadGuardTest {
    @Test
    void malformedJsonReturnsNull() {
        assertNull(WiredExtraPayloadGuard.fromJson("{broken", WiredExtraAnimationTime.JsonData.class));
        assertNull(WiredExtraPayloadGuard.fromJson(null, WiredExtraAnimationTime.JsonData.class));
    }
}
