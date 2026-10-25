package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTeleporter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The game item maps of a room were HashMaps changed from packet threads while game cycles iterated
 * them without the lock (getRandomTeleporter among others): a ConcurrentModificationException or a
 * corrupted map mid-game. Picking a random freeze exit tile threw from nextInt(0) in a room with none.
 */
class RoomSpecialTypesConcurrencyTest {

    private static InteractionBattleBanzaiTeleporter teleporter(int id) {
        InteractionBattleBanzaiTeleporter teleporter = mock(InteractionBattleBanzaiTeleporter.class);
        when(teleporter.getId()).thenReturn(id);
        return teleporter;
    }

    @Test
    void teleportersCanBePickedWhileOthersAreAddedAndRemoved() throws Exception {
        RoomSpecialTypes types = new RoomSpecialTypes();
        List<InteractionBattleBanzaiTeleporter> teleporters = new ArrayList<>();
        for (int id = 1; id <= 200; id++) {
            teleporters.add(teleporter(id));
        }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        long until = System.nanoTime() + 300_000_000L;

        Thread changer = Thread.ofPlatform().start(() -> {
            while (System.nanoTime() < until) {
                for (InteractionBattleBanzaiTeleporter teleporter : teleporters) {
                    types.addBanzaiTeleporter(teleporter);
                }
                for (InteractionBattleBanzaiTeleporter teleporter : teleporters) {
                    types.removeBanzaiTeleporter(teleporter);
                }
            }
        });
        Thread picker = Thread.ofPlatform().start(() -> {
            try {
                while (System.nanoTime() < until) {
                    types.getRandomTeleporter(null, null);
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        changer.join(5_000);
        picker.join(5_000);

        assertNull(failure.get(), () -> "picking a teleporter while they change threw " + failure.get());
    }

    @Test
    void aRoomWithoutFreezeExitTilesHasNoneToPick() {
        RoomSpecialTypes types = new RoomSpecialTypes();

        assertNull(types.getRandomFreezeExitTile());
        assertTrue(!types.hasFreezeExitTile());
    }
}
