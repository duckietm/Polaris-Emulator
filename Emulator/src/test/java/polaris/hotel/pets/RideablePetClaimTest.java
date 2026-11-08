package polaris.hotel.pets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;

/** One rider per horse, and only on a saddle. */
class RideablePetClaimTest {

    private static RideablePet horse(boolean saddled) {
        RideablePet horse = mock(RideablePet.class, CALLS_REAL_METHODS);
        horse.hasSaddle(saddled);
        return horse;
    }

    @Test
    void theSecondRiderFindsTheHorseTaken() {
        RideablePet horse = horse(true);
        User first = mock(User.class);

        assertTrue(horse.claimRider(first));
        assertFalse(horse.claimRider(mock(User.class)));
        assertSame(first, horse.getRider());
    }

    @Test
    void aHorseWithoutSaddleCannotBeRidden() {
        assertFalse(horse(false).claimRider(mock(User.class)));
    }
}
