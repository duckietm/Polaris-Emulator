package polaris.hotel.achievements.resolution;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;

class AchievementResolutionManagerNullTest {
    private final AchievementResolutionManager manager = AchievementResolutionManager.getInstance();
    private final User user = mock(User.class);
    private final AchievementResolution resolution = new AchievementResolution(100, 7, 3, 2, "ACH_Test2", 0, 0, 0);

    @Test
    void aMissingResolutionHasNoProgressInsteadOfFailing() {
        assertArrayEquals(new int[] {0, 0}, manager.progress(user, null));
    }

    @Test
    void aMissingResolutionIsNeverKept() {
        assertFalse(manager.kept(user, null));
    }

    @Test
    void aMissingUserHasNoProgressAndKeepsNothing() {
        assertArrayEquals(new int[] {0, 0}, manager.progress(null, resolution));
        assertFalse(manager.kept(null, resolution));
    }
}
