package com.eu.habbo.habbohotel.achievements.resolution;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.users.Habbo;
import org.junit.jupiter.api.Test;

class AchievementResolutionManagerNullTest {
    private final AchievementResolutionManager manager = AchievementResolutionManager.getInstance();
    private final Habbo habbo = mock(Habbo.class);
    private final AchievementResolution resolution = new AchievementResolution(100, 7, 3, 2, "ACH_Test2", 0, 0, 0);

    @Test
    void aMissingResolutionHasNoProgressInsteadOfFailing() {
        assertArrayEquals(new int[] {0, 0}, manager.progress(habbo, null));
    }

    @Test
    void aMissingResolutionIsNeverKept() {
        assertFalse(manager.kept(habbo, null));
    }

    @Test
    void aMissingUserHasNoProgressAndKeepsNothing() {
        assertArrayEquals(new int[] {0, 0}, manager.progress(null, resolution));
        assertFalse(manager.kept(null, resolution));
    }
}
