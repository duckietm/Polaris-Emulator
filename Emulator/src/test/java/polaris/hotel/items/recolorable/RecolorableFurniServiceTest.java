package polaris.hotel.items.recolorable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionRecolorable;

/** The official recolour window's four scopes. */
class RecolorableFurniServiceTest {

    private static InteractionRecolorable item(int baseId, String extradata) {
        Item base = mock(Item.class);
        when(base.getId()).thenReturn(baseId);
        return new InteractionRecolorable(1, 1, base, extradata, 0, 0);
    }

    @Test
    void scopesMatchTheOfficialWindow() {
        int red = 0xFF0000;
        InteractionRecolorable sameBoth = item(7, "0|255,0,0");
        InteractionRecolorable sameColor = item(8, "0|255,0,0");
        InteractionRecolorable sameType = item(7, "0|0,0,255");

        assertTrue(RecolorableFurniService.matches(RecolorableFurniService.SCOPE_SAME_COLOR, sameColor, 7, red));
        assertFalse(RecolorableFurniService.matches(RecolorableFurniService.SCOPE_SAME_COLOR, sameType, 7, red));
        assertTrue(RecolorableFurniService.matches(RecolorableFurniService.SCOPE_SAME_TYPE, sameType, 7, red));
        assertFalse(RecolorableFurniService.matches(RecolorableFurniService.SCOPE_SAME_TYPE, sameColor, 7, red));
        assertTrue(
                RecolorableFurniService.matches(RecolorableFurniService.SCOPE_SAME_TYPE_AND_COLOR, sameBoth, 7, red));
        assertFalse(
                RecolorableFurniService.matches(RecolorableFurniService.SCOPE_SAME_TYPE_AND_COLOR, sameType, 7, red));
    }

    @Test
    void channelsAreBytes() {
        assertTrue(RecolorableFurniService.isChannel(0));
        assertTrue(RecolorableFurniService.isChannel(255));
        assertFalse(RecolorableFurniService.isChannel(256));
        assertFalse(RecolorableFurniService.isChannel(-1));
    }
}
