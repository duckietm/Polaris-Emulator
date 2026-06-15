package com.eu.habbo.habbohotel.users.infostand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InfostandBackgroundManagerTest {
    @Test
    void summaryKeepsStartupLogCompact() {
        assertEquals(
                "Infostand Background Manager -> Loaded! (260 assets)",
                InfostandBackgroundManager.summary(188, 22, 9, 16, 25));
    }
}
