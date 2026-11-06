package polaris.hotel.wired.arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.WiredLargePayload;
import polaris.hotel.items.interactions.wired.extra.WiredExtraArrayCaptureVariable;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.WiredEffectType;

class WiredArrayProtocolContractTest {

    @Test
    void keepsBackendLayoutCodesAndBoundedPayloadInSync() {
        assertEquals(126, WiredEffectType.MODIFY_ARRAY.code);
        assertEquals(1014, WiredConditionType.CHECK_ARRAY.code); // not an official condition: 1000 and up
        assertEquals(127, WiredExtraArrayCaptureVariable.CODE);
        assertEquals(32_768, WiredLargePayload.MAX_STRING_PARAM_LENGTH);
    }
}
