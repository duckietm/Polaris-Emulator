package polaris.hotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UserInfoPhotoClaimTest {
    @Test
    void aPhotoCanBeClaimedForPurchaseOnce() {
        UserInfo info = new UserInfo(1, 0);

        assertFalse(info.claimPhotoPurchase(0));
        assertTrue(info.claimPhotoPurchase(100));
        assertFalse(info.claimPhotoPurchase(100));

        assertTrue(info.claimPhotoPurchase(200), "a new photo can be bought");
        assertFalse(info.claimPhotoPurchase(200));
    }

    @Test
    void aReleasedClaimCanBeTakenAgain() {
        UserInfo info = new UserInfo(1, 0);

        assertTrue(info.claimPhotoPurchase(100));
        info.releasePhotoPurchase(100);
        assertTrue(info.claimPhotoPurchase(100));
    }

    @Test
    void theWebPublishSlotIsTakenOncePerDelay() {
        UserInfo info = new UserInfo(1, 0);

        assertEquals(0, info.claimWebPublish(1_000, 180));
        assertEquals(-1, info.claimWebPublish(1_000, 180));
        assertEquals(-1, info.claimWebPublish(1_179, 180));
        assertEquals(1_000, info.claimWebPublish(1_180, 180));
    }

    @Test
    void aReleasedPublishSlotRestoresThePreviousPublish() {
        UserInfo info = new UserInfo(1, 0);
        info.setWebPublishTimestamp(500);

        int previous = info.claimWebPublish(1_000, 180);
        assertEquals(500, previous);
        info.releaseWebPublish(1_000, previous);

        assertEquals(500, info.getWebPublishTimestamp());
    }
}
