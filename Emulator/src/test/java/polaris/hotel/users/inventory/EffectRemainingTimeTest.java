package polaris.hotel.users.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;

class EffectRemainingTimeTest {

    private static EffectsComponent.UserEffect effect(int duration, int activatedAt) {
        EffectsComponent.UserEffect effect = new EffectsComponent.UserEffect(1, 1);
        effect.duration = duration;
        effect.activationTimestamp = activatedAt;
        return effect;
    }

    @Test
    void countsDownFromTheActivation() {
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class, CALLS_REAL_METHODS)) {
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(1_000_100);

            assertEquals(3500, effect(3600, 1_000_000).remainingTime());
            assertEquals(3600, effect(3600, -1).remainingTime());
            assertEquals(Integer.MAX_VALUE, effect(0, 1_000_000).remainingTime());
        }
    }

    @Test
    void anExpiredEffectIsNotRemaining() {
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class, CALLS_REAL_METHODS)) {
            emulator.when(Emulator::getIntUnixTimestamp).thenReturn(1_010_000);

            EffectsComponent.UserEffect expired = effect(3600, 1_000_000);
            assertFalse(expired.isRemaining());
            assertEquals(0, expired.total);
        }
    }
}
