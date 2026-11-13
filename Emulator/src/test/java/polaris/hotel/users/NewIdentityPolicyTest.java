package polaris.hotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NewIdentityPolicyTest {
    private static final int DAY = 86_400;
    private static final int NOW = 2_000_000_000;

    @Test
    void anAccountIsNewForItsFirstDays() {
        assertEquals(NewIdentityPolicy.NEW_IDENTITY, NewIdentityPolicy.status(NOW, NOW, 7));
        assertEquals(NewIdentityPolicy.NEW_IDENTITY, NewIdentityPolicy.status(NOW - 7 * DAY + 1, NOW, 7));
        assertEquals(NewIdentityPolicy.OLD_IDENTITY, NewIdentityPolicy.status(NOW - 7 * DAY, NOW, 7));
        assertEquals(NewIdentityPolicy.OLD_IDENTITY, NewIdentityPolicy.status(NOW - 400 * DAY, NOW, 7));
    }

    @Test
    void zeroDaysMakesNoAccountNew() {
        assertEquals(NewIdentityPolicy.OLD_IDENTITY, NewIdentityPolicy.status(NOW, NOW, 0));
    }

    @Test
    void aNegativeSettingKeepsEveryAccountNewAsBefore() {
        assertEquals(NewIdentityPolicy.NEW_IDENTITY, NewIdentityPolicy.status(NOW - 400 * DAY, NOW, -1));
        assertEquals(NewIdentityPolicy.NEW_IDENTITY, NewIdentityPolicy.status(0, NOW, -1));
    }

    @Test
    void anAccountWithoutACreationTimeIsOld() {
        assertEquals(NewIdentityPolicy.OLD_IDENTITY, NewIdentityPolicy.status(0, NOW, 7));
    }

    @Test
    void aLongWindowDoesNotOverflow() {
        assertEquals(NewIdentityPolicy.NEW_IDENTITY, NewIdentityPolicy.status(NOW - 100 * DAY, NOW, 40_000));
    }
}
