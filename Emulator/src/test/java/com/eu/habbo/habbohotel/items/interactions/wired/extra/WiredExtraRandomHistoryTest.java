package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class WiredExtraRandomHistoryTest {

    private static void set(Object target, String name, int value) throws ReflectiveOperationException {
        Field field = WiredExtraRandom.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    @Test
    void pickOneAvoidTwoAlternatesTwoActions() throws ReflectiveOperationException {
        WiredExtraRandom random = new WiredExtraRandom(1, 1, mock(Item.class), "", 0, 0);
        set(random, "pickAmount", 1);
        set(random, "skipExecutions", 2);

        IWiredEffect first = context -> {};
        IWiredEffect second = context -> {};
        List<IWiredEffect> effects = List.of(first, second);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getRandom).thenReturn(new Random(7));

            IWiredEffect previous = random.selectWiredEffects(effects).getFirst();
            for (int run = 0; run < 20; run++) {
                List<IWiredEffect> picked = random.selectWiredEffects(effects);

                assertEquals(1, picked.size());
                assertNotEquals(previous, picked.getFirst());
                previous = picked.getFirst();
            }
        }
    }
}
