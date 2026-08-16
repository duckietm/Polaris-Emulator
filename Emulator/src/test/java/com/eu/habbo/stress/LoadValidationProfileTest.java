package com.eu.habbo.stress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LoadValidationProfileTest {
    private final StressLimits limits = new StressLimits(5_000, 100_000, 50_000, 50_000, 100, 200_000, 10_000, 3_600);

    @Test
    void everyPresetIsBoundedByTheDefaultSafetyLimits() {
        for (LoadValidationProfile profile : LoadValidationProfile.values()) {
            StressScenario scenario = profile.scenario(41, 7, 42L, true).validate(this.limits);

            assertEquals(41, scenario.roomId());
            assertEquals(7, scenario.itemId());
        }
    }

    @Test
    void profileLookupIsCaseInsensitiveAndRejectsUnknownNames() {
        assertEquals(LoadValidationProfile.SMALL, LoadValidationProfile.parse("Small"));
        assertThrows(IllegalArgumentException.class, () -> LoadValidationProfile.parse("unbounded"));
    }

    @Test
    void largePresetRemainsTimeAndEntityBounded() {
        StressScenario scenario = LoadValidationProfile.LARGE.scenario(41, 7, 42L, true);

        assertEquals(300, scenario.bots());
        assertEquals(10_000, scenario.items());
        assertEquals(180, scenario.durationSeconds());
    }
}
