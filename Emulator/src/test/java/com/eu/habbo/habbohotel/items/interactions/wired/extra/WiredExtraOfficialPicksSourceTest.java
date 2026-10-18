package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.wired.protocol.WiredInputSourcesConf;
import org.junit.jupiter.api.Test;

/** The official editor names the curve's and the variable filters' reference picks 100; the boxes keep them as 101. */
class WiredExtraOfficialPicksSourceTest {

    @Test
    void theCurveTakesTheOfficialPicksSourceAndStillTheStoredOne() throws Exception {
        assertEquals(101, curveSourceAfterSaving(100));
        assertEquals(101, curveSourceAfterSaving(101));
        assertEquals(200, curveSourceAfterSaving(200));
        assertEquals(0, curveSourceAfterSaving(0));
    }

    @Test
    void theEditorIsOfferedThePicksAs100() {
        for (String interaction :
                new String[] {"wf_xtra_mov_curve", "wf_xtra_filter_furni_by_var", "wf_xtra_filter_users_by_var"}) {
            WiredInputSourcesConf conf = WiredInputSourcesConf.forInteraction(interaction);
            assertArrayEquals(new int[] {0, 100, 200, 201}, conf.allowedFurni()[0], interaction);
            assertArrayEquals(new int[] {0}, conf.defaultFurni(), interaction);
        }
    }

    private static int curveSourceAfterSaving(int furniSource) throws Exception {
        WiredExtraMovementCurve curve = new WiredExtraMovementCurve(1, 1, mock(Item.class), "", 0, 0);
        WiredSettings settings =
                new WiredSettings(new int[] {0, 80, 1, WiredExtraMovementCurve.CURVE_LINEAR}, "", new int[0], 0);
        settings.setFurniSourceTypes(new int[] {furniSource});
        curve.saveData(settings, null);
        return curve.officialFurniSources()[0];
    }
}
