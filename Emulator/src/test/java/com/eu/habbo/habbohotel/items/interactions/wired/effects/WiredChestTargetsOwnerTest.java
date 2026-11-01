package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.InteractionWiredChestCurrency;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.wired.WiredSaveException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The give-from-chest boxes took from any upgraded, unlocked chest in the room: a room owner's box handed out the
 * coins and furni other users had put in their own chests.
 */
class WiredChestTargetsOwnerTest {
    private static final int OWNER = 7;

    private static InteractionWiredChestCurrency chest(int id, int ownerId) {
        InteractionWiredChestCurrency chest = mock(InteractionWiredChestCurrency.class, RETURNS_DEEP_STUBS);
        when(chest.getId()).thenReturn(id);
        when(chest.getUserId()).thenReturn(ownerId);
        when(chest.answersWired()).thenReturn(true);
        when(chest.getContents().isLocked()).thenReturn(false);
        return chest;
    }

    @Test
    void onlyTheBoxOwnersChestsAreUsed() {
        InteractionWiredChestCurrency theirs = chest(1, 99);
        InteractionWiredChestCurrency own = chest(2, OWNER);
        List<HabboItem> candidates = List.of(theirs, own);

        assertEquals(List.of(own), WiredChestTargets.allUsable(candidates, InteractionWiredChestCurrency.class, OWNER));
        assertSame(own, WiredChestTargets.firstUsable(candidates, InteractionWiredChestCurrency.class, OWNER));
        assertNull(WiredChestTargets.firstUsable(List.of(theirs), InteractionWiredChestCurrency.class, OWNER));
    }

    @Test
    void aSaveThatPicksSomeoneElsesChestIsRefused() {
        Room room = mock(Room.class);
        InteractionWiredChestCurrency theirs = chest(1, 99);
        when(room.getHabboItem(1)).thenReturn(theirs);

        assertThrows(
                WiredSaveException.class,
                () -> WiredChestTargets.validatePicks(
                        room,
                        new int[] {1},
                        InteractionWiredChestCurrency.class,
                        WiredChestTargets.REQUIRE_COIN_CHESTS,
                        OWNER));
    }
}
