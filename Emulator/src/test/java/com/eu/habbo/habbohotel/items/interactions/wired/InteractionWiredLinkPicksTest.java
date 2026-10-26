package com.eu.habbo.habbohotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;

/** AIR refuses a link box's picks with a furni that is not a Link; a missing furni is not checked. */
class InteractionWiredLinkPicksTest {
    @Test
    void aPickedFurniThatIsNoLinkIsRefused() {
        Room room = mock(Room.class);
        when(room.getHabboItem(1)).thenReturn(mock(InteractionWiredLink.class));
        when(room.getHabboItem(2)).thenReturn(mock(HabboItem.class));

        assertDoesNotThrow(() -> InteractionWiredLink.refuseNonLinkPicks(room, new int[] {1, 999}));
        WiredSaveRefusedException refused = assertThrows(
                WiredSaveRefusedException.class, () -> InteractionWiredLink.refuseNonLinkPicks(room, new int[] {1, 2}));
        assertEquals(InteractionWiredLink.REQUIRE_LINK_FURNI, refused.getMessage());
    }
}
