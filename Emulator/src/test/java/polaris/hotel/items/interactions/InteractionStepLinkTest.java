package polaris.hotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.permissions.Permission;
import polaris.hotel.pets.RideablePet;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/** room_noob_exit: the room id kept as the custom value internalLink; the user who steps on it goes there. */
class InteractionStepLinkTest {

    private static InteractionStepLink exit(String value) {
        InteractionStepLink exit = new InteractionStepLink(1, 1, mock(Item.class), "", 0, 0);
        exit.values.put(InteractionStepLink.LINK_KEY, value);
        return exit;
    }

    @Test
    void anExitSendsTheUserToItsRoom() {
        assertEquals("navigator/goto/8889", exit("8889").getLink());
        assertEquals("navigator/goto/8889", exit(" 8889 ").getLink());
        assertEquals("navigator/goto/8889", exit("navigator/goto/8889").getLink(), "a link saved before still counts");
    }

    @Test
    void anExitWithoutARoomSendsNothing() {
        assertNull(exit("").getLink());
        assertNull(exit("0").getLink(), "the state a new furni gets is no room");
        assertNull(exit("habbopages/help").getLink(), "only rooms");
        assertNull(exit("-5").getLink());
        assertNull(exit("99999999999").getLink(), "not a room id");
    }

    // Staff set the room id in the infostand ("Room ID"); the save goes through sanitizeValue.
    @Test
    void aSaveKeepsARoomIdClearsWithEmptyAndRefusesTheRest() {
        InteractionStepLink exit = exit("8889");

        assertTrue(exit.acceptsKey(InteractionStepLink.LINK_KEY));
        assertFalse(exit.acceptsKey("state"), "only the room can be set");
        assertEquals("1234", exit.sanitizeValue(InteractionStepLink.LINK_KEY, " 1234 "));
        assertEquals("1234", exit.sanitizeValue(InteractionStepLink.LINK_KEY, "navigator/goto/1234"));
        assertEquals("", exit.sanitizeValue(InteractionStepLink.LINK_KEY, ""), "an empty value clears the room");
        assertEquals("", exit.sanitizeValue(InteractionStepLink.LINK_KEY, " "));
        assertEquals("", exit.sanitizeValue(InteractionStepLink.LINK_KEY, "0"), "0 clears the room too");
        assertNull(exit("").getLink(), "a cleared exit does nothing");
        assertNull(
                exit.sanitizeValue(InteractionStepLink.LINK_KEY, "catalog/open/x"), "not a room keeps the stored one");
    }

    @Test
    void onlyStaffMayPointAnExitSomewhere() {
        User staff = mock(User.class);
        when(staff.hasPermission(Permission.ACC_ANYROOMOWNER)).thenReturn(true);

        assertTrue(InteractionStepLink.mayEdit(staff));
        assertFalse(InteractionStepLink.mayEdit(mock(User.class)));
        assertFalse(InteractionStepLink.mayEdit(null));
    }

    // The same rules as the arrow: only a walk that ends on the exit, and not while teleporting or riding.
    @Test
    void onlyAWalkThatEndsOnTheExitUsesIt() {
        InteractionStepLink exit = new InteractionStepLink(1, 1, mock(Item.class), "", 0, 0);
        exit.setX((short) 3);
        exit.setY((short) 4);
        RoomUnit unit = mock(RoomUnit.class);

        when(unit.getGoal()).thenReturn(new RoomTile((short) 3, (short) 4, (short) 0, RoomTileState.OPEN, true));
        assertTrue(InteractionArrow.walkEndsOn(unit, exit));

        unit.isTeleporting = true;
        assertFalse(InteractionArrow.walkEndsOn(unit, exit), "not while a teleport runs");

        unit.isTeleporting = false;
        when(unit.getGoal()).thenReturn(new RoomTile((short) 5, (short) 4, (short) 0, RoomTileState.OPEN, true));
        assertFalse(InteractionArrow.walkEndsOn(unit, exit), "walking over it on the way somewhere else");
    }

    @Test
    void aUserRidingAPetDoesNotTravel() {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(user.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        when(user.getUserInfo()).thenReturn(info);

        assertTrue(InteractionStepLink.mayTravel(user));

        when(info.getRiding()).thenReturn(mock(RideablePet.class));
        assertFalse(InteractionStepLink.mayTravel(user));
    }
}
