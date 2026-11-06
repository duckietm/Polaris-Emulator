package polaris.hotel.items.interactions.wired;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;

/** AIR refuses a link box's picks with a furni that is not a Link; a missing furni is not checked. */
class InteractionWiredLinkPicksTest {
    @Test
    void aPickedFurniThatIsNoLinkIsRefused() {
        Room room = mock(Room.class);
        when(room.getUserItem(1)).thenReturn(mock(InteractionWiredLink.class));
        when(room.getUserItem(2)).thenReturn(mock(UserItem.class));

        assertDoesNotThrow(() -> InteractionWiredLink.refuseNonLinkPicks(room, new int[] {1, 999}));
        WiredSaveRefusedException refused = assertThrows(
                WiredSaveRefusedException.class, () -> InteractionWiredLink.refuseNonLinkPicks(room, new int[] {1, 2}));
        assertEquals(InteractionWiredLink.REQUIRE_LINK_FURNI, refused.getMessage());
    }
}
