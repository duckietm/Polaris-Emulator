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
}
