package polaris.hotel.items.interactions.wired.selector;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WiredSelectorPayloadGuardTest {
    @Test
    void malformedJsonReturnsNull() {
        assertNull(WiredSelectorPayloadGuard.fromJson("{broken", WiredEffectFurniArea.JsonData.class));
        assertNull(WiredSelectorPayloadGuard.fromJson(null, WiredEffectFurniArea.JsonData.class));
    }
}
