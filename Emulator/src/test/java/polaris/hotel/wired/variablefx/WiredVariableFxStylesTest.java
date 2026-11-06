package polaris.hotel.wired.variablefx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import polaris.hotel.games.GameTeamColors;

/** The style table is what the client draws from; a lookup must always land on something drawable. */
class WiredVariableFxStylesTest {

    @Test
    void everyCategoryHasAtLeastOneStyleWithARenderer() {
        for (int category = WiredVariableFxStyles.CATEGORY_HEALTH_POINTS;
                category <= WiredVariableFxStyles.CATEGORY_NUMBER_DISPLAY;
                category++) {
            assertTrue(WiredVariableFxStyles.styleCount(category) > 0, "category " + category);
            for (int styleId = 0; styleId < WiredVariableFxStyles.styleCount(category); styleId++) {
                WiredVariableFxStyles.Style style = WiredVariableFxStyles.get(category, styleId);
                assertEquals(styleId, style.styleId(), "category " + category + " style " + styleId);
                assertTrue(style.rendererIds().length > 0);
            }
        }
    }

    @Test
    void anUnknownStyleOrCategoryFallsBackToSomethingDrawable() {
        assertEquals(
                0,
                WiredVariableFxStyles.get(WiredVariableFxStyles.CATEGORY_HEALTH_POINTS, 99)
                        .styleId());
        assertEquals(
                0,
                WiredVariableFxStyles.get(WiredVariableFxStyles.CATEGORY_HEALTH_POINTS, -1)
                        .styleId());
        assertEquals(
                WiredVariableFxStyles.RENDERER_PLAIN,
                WiredVariableFxStyles.get(42, 0).defaultRendererId());
        assertEquals(0, WiredVariableFxStyles.styleCount(42));
    }

    @Test
    void aStyleOnlyResolvesRenderersItRegisters() {
        WiredVariableFxStyles.Style status = WiredVariableFxStyles.get(WiredVariableFxStyles.CATEGORY_STATUS_BAR, 0);

        assertEquals(
                WiredVariableFxStyles.RENDERER_STRIPED, status.resolveRenderer(WiredVariableFxStyles.RENDERER_STRIPED));
        assertEquals(status.defaultRendererId(), status.resolveRenderer(WiredVariableFxStyles.RENDERER_BOSS));
    }

    @Test
    void segmentsOnlyApplyToTheSegmentedRenderers() {
        assertTrue(WiredVariableFxStyles.supportsSegments(WiredVariableFxStyles.RENDERER_BLOCK));
        assertTrue(WiredVariableFxStyles.supportsSegments(WiredVariableFxStyles.RENDERER_ARROW));
        assertTrue(WiredVariableFxStyles.supportsSegments(WiredVariableFxStyles.RENDERER_THERMOMETER));
        assertFalse(WiredVariableFxStyles.supportsSegments(WiredVariableFxStyles.RENDERER_PLAIN));
        assertFalse(WiredVariableFxStyles.supportsSegments(WiredVariableFxStyles.RENDERER_HEARTS));
    }

    @Test
    void teamColoursCoverTheFourTeamsAndNothingElse() {
        assertEquals("#df291e", WiredVariableFxStyles.teamColor(GameTeamColors.RED));
        assertEquals("#36b24a", WiredVariableFxStyles.teamColor(GameTeamColors.GREEN));
        assertEquals("#3b7de3", WiredVariableFxStyles.teamColor(GameTeamColors.BLUE));
        assertEquals("#ffd83d", WiredVariableFxStyles.teamColor(GameTeamColors.YELLOW));
        assertNull(WiredVariableFxStyles.teamColor(null));
    }

    @Test
    void iconsAreOnlyTheOnesTheClientShips() {
        assertTrue(WiredVariableFxStyles.isKnownIcon("misc_heart"));
        assertFalse(WiredVariableFxStyles.isKnownIcon(""));
        assertFalse(WiredVariableFxStyles.isKnownIcon(null));
        assertFalse(WiredVariableFxStyles.isKnownIcon("../etc/passwd"));
    }
}
