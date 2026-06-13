package com.eu.habbo.habbohotel.users.infostand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InfostandBackgroundManagerTest {
    @Test
    void summaryKeepsStartupLogCompact() {
        assertEquals(
                "Infostand Background Manager -> Loaded! (260 assets: 188 bg, 22 stands, 9 overlays, 16 cards, 25 borders)",
                InfostandBackgroundManager.summary(188, 22, 9, 16, 25));
    }
}
