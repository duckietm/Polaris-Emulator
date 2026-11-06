package polaris.hotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.base;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.context;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.json;
import static polaris.hotel.items.interactions.wired.effects.WiredEffectTestFixtures.row;

import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/**
 * "Mute user" clamps its length on save and on execute but not on load, used the user's client
 * without checking it was still there, and could carry a null message after a short legacy row.
 */
class WiredEffectMuteUserTest {

    @Test
    void aStoredLengthPastTheCapLoadsClamped() throws Exception {
        WiredEffectMuteUser box = new WiredEffectMuteUser(1, 1, base(), "", 0, 0);

        box.loadWiredData(row("{\"delay\":0,\"length\":99999,\"userSource\":0}"), null);

        assertEquals(24 * 60, json(box).get("length").getAsInt());
    }

    @Test
    void aRowWithoutAMessageLoadsAnEmptyOne() throws Exception {
        WiredEffectMuteUser box = new WiredEffectMuteUser(1, 1, base(), "", 0, 0);

        box.loadWiredData(row("{\"delay\":0,\"length\":5,\"userSource\":0}"), null);

        assertEquals("", json(box).get("message").getAsString());
    }

    @Test
    void aUserWhoseClientIsGoneIsStillMutedAndNothingFails() throws Exception {
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(mock(UserInfo.class));
        when(room.getUser(unit)).thenReturn(user);
        WiredEffectMuteUser box = new WiredEffectMuteUser(1, 1, base(), "", 0, 0);
        box.loadWiredData(row("{\"delay\":0,\"length\":5,\"message\":\"Quiet\",\"userSource\":0}"), room);

        box.execute(context(room, unit));

        verify(room).muteUser(user, 5);
    }
}
