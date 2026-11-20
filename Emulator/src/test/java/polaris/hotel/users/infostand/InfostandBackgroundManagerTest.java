package polaris.hotel.users.infostand;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InfostandBackgroundManagerTest {
    @Test
    void summaryKeepsStartupLogCompact() {
        assertEquals(
                "Infostand Background Manager -> Loaded! (260 assets)",
                InfostandBackgroundManager.summary(188, 22, 9, 16, 25));
    }

    @Test
    void summaryCountsBannersAndAccents() {
        assertEquals(
                "Infostand Background Manager -> Loaded! (280 assets)",
                InfostandBackgroundManager.summary(188, 22, 9, 16, 25, 8, 12));
    }

    @Test
    void bannerAndAccentAreCategoriesOfTheSameTable() {
        assertEquals(
                InfostandBackgroundManager.Category.BANNER, InfostandBackgroundManager.Category.fromDbValue("banner"));
        assertEquals(
                InfostandBackgroundManager.Category.ACCENT, InfostandBackgroundManager.Category.fromDbValue("accent"));
    }
}
