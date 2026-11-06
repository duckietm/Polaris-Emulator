package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;

/**
 * A furni's states run from 0 to stateCount - 1. The box drew from stateCount + 1 values, so one
 * roll in stateCount + 1 wrote a state the furni does not have.
 */
class WiredEffectToggleRandomTest {

    @Test
    void theHighestRollIsTheLastState() throws Exception {
        Room room = mock(Room.class);
        UserItem item = furni(room, 501, 3);
        WiredEffectToggleRandom box = box(room);
        HighestRoll random = new HighestRoll();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getRandom).thenReturn(random);

            box.execute(context(room));
        }

        assertEquals(List.of(3), random.bounds);
        verify(item).setExtradata("2");
    }

    @Test
    void aFurniWithNoStatesStillGetsStateZero() throws Exception {
        Room room = mock(Room.class);
        UserItem item = furni(room, 501, 0);
        WiredEffectToggleRandom box = box(room);
        HighestRoll random = new HighestRoll();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getRandom).thenReturn(random);

            box.execute(context(room));
        }

        assertEquals(List.of(1), random.bounds);
        verify(item).setExtradata("0");
    }

    /** A random that always answers the largest value its bound allows, and remembers the bounds it was asked for. */
    private static final class HighestRoll extends Random {
        final List<Integer> bounds = new ArrayList<>();

        @Override
        public int nextInt(int bound) {
            this.bounds.add(bound);
            return bound - 1;
        }
    }

    private static WiredEffectToggleRandom box(Room room) throws Exception {
        WiredEffectToggleRandom box = new WiredEffectToggleRandom(1, 1, base(), "", 0, 0);
        box.loadWiredData(row("{\"delay\":0,\"itemIds\":[501],\"furniSource\":100}"), room);
        return box;
    }

    private static UserItem furni(Room room, int id, int stateCount) {
        Item base = base();
        when(base.getStateCount()).thenReturn(stateCount);
        UserItem item = mock(UserItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getRoomId()).thenReturn(1);
        when(item.getBaseItem()).thenReturn(base);
        when(room.getUserItem(id)).thenReturn(item);
        return item;
    }
}
