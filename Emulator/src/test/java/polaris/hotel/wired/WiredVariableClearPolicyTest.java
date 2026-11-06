package polaris.hotel.wired;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraUserVariable;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;

/**
 * Clearing a variable from every holder is the one management action that reaches people who are
 * not in the room, so it is reserved for the owner of the definition box, the way the official
 * client reserves it ("Only the owner of this variable can perform this action").
 */
class WiredVariableClearPolicyTest {

    private static final int OWNER_ID = 41, OTHER_ID = 42, DEFINITION_ID = 900;

    @Test
    void theOwnerOfTheDefinitionBoxMayClearIt() {
        Room room = roomWith(userDefinition(OWNER_ID), true);

        assertTrue(WiredVariableClearPolicy.canClear(room, user(OWNER_ID, false), DEFINITION_ID));
    }

    @Test
    void aFurniVariableBoxCountsToo() {
        Room room = roomWith(furniDefinition(OWNER_ID), true);

        assertTrue(WiredVariableClearPolicy.canClear(room, user(OWNER_ID, false), DEFINITION_ID));
    }

    @Test
    void somebodyElseWithWiredRightsMayNot() {
        Room room = roomWith(userDefinition(OWNER_ID), true);

        assertFalse(WiredVariableClearPolicy.canClear(room, user(OTHER_ID, false), DEFINITION_ID));
    }

    @Test
    void staffWhoOwnEveryRoomMay() {
        Room room = roomWith(userDefinition(OWNER_ID), true);

        assertTrue(WiredVariableClearPolicy.canClear(room, user(OTHER_ID, true), DEFINITION_ID));
    }

    @Test
    void theOwnerStillNeedsWiredRightsInTheRoom() {
        Room room = roomWith(userDefinition(OWNER_ID), false);

        assertFalse(WiredVariableClearPolicy.canClear(room, user(OWNER_ID, false), DEFINITION_ID));
    }

    @Test
    void aBoxThatIsNotAVariableDefinitionCannotBeCleared() {
        UserItem notADefinition = mock(UserItem.class);
        when(notADefinition.getUserId()).thenReturn(OWNER_ID);
        Room room = roomWith(notADefinition, true);

        assertFalse(WiredVariableClearPolicy.canClear(room, user(OWNER_ID, false), DEFINITION_ID));
    }

    @Test
    void aMissingBoxOrRoomOrHabboIsRefused() {
        Room room = roomWith(null, true);

        assertFalse(WiredVariableClearPolicy.canClear(room, user(OWNER_ID, false), DEFINITION_ID));
        assertFalse(WiredVariableClearPolicy.canClear(null, user(OWNER_ID, false), DEFINITION_ID));
        assertFalse(WiredVariableClearPolicy.canClear(room, null, DEFINITION_ID));
    }

    private static Room roomWith(UserItem definition, boolean canModify) {
        Room room = mock(Room.class);
        when(room.getUserItem(DEFINITION_ID)).thenReturn(definition);
        when(room.canModifyWired(org.mockito.ArgumentMatchers.any())).thenReturn(canModify);
        return room;
    }

    private static UserItem userDefinition(int ownerId) {
        WiredExtraUserVariable definition = mock(WiredExtraUserVariable.class);
        when(definition.getUserId()).thenReturn(ownerId);
        return definition;
    }

    private static UserItem furniDefinition(int ownerId) {
        WiredExtraFurniVariable definition = mock(WiredExtraFurniVariable.class);
        when(definition.getUserId()).thenReturn(ownerId);
        return definition;
    }

    private static User user(int id, boolean ownsEveryRoom) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(id);
        when(user.getUserInfo()).thenReturn(info);
        when(user.hasPermission(Permission.ACC_ANYROOMOWNER)).thenReturn(ownsEveryRoom);
        return user;
    }
}
