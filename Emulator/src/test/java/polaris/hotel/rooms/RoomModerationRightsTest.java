package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/**
 * A group member who may decorate the group's room holds building rights only: kicking, muting, banning and
 * sending someone else's furni back need real room rights, a group admin, or the owner.
 */
class RoomModerationRightsTest {
    @Test
    void onlyLevelsAboveGroupMemberModerate() {
        assertFalse(RoomRightsManager.moderates(null));
        assertFalse(RoomRightsManager.moderates(RoomRightLevels.NONE));
        assertFalse(RoomRightsManager.moderates(RoomRightLevels.GUILD_RIGHTS));
        assertTrue(RoomRightsManager.moderates(RoomRightLevels.RIGHTS));
        assertTrue(RoomRightsManager.moderates(RoomRightLevels.GUILD_ADMIN));
        assertTrue(RoomRightsManager.moderates(RoomRightLevels.MODERATOR));
    }

    @Test
    void ownerModeratesWithoutBeingInTheRoom() {
        Room room = RoomTestBuilder.room(41, 7).build();

        assertTrue(room.hasModerationRights(user(7)));
    }

    @Test
    void aStrangerOutsideTheRoomDoesNotModerate() {
        Room room = RoomTestBuilder.room(41, 7).build();

        assertFalse(room.hasModerationRights(user(12)));
        assertFalse(room.hasModerationRights(null));
    }

    private static User user(int id) {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(user.getUserInfo()).thenReturn(info);
        when(info.getId()).thenReturn(id);
        return user;
    }
}
