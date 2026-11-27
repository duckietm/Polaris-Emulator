package polaris.hotel.users.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;

class ProfileAvatarPoseTest {

    @Test
    void theDefaultIsTodaysLook() {
        assertEquals(new ProfileAvatarPose(2, 0, 0), ProfileAvatarPose.DEFAULT);
    }

    @Test
    void aStoredPoseIsMadeSafeToDraw() {
        assertEquals(new ProfileAvatarPose(2, 16, -16), ProfileAvatarPose.of(9, 120, -128));
        assertEquals(new ProfileAvatarPose(7, -3, 4), ProfileAvatarPose.of(7, -3, 4));
    }

    @Test
    void aRequestKeepsTheSavedDirectionWhenItsDirectionIsUnknown() {
        ProfileAvatarPose saved = new ProfileAvatarPose(5, 1, 1);

        assertSame(saved, ProfileAvatarPose.resolve(ProfileAvatarPose.KEEP, 9, 9, saved));
        assertEquals(new ProfileAvatarPose(5, 16, 0), ProfileAvatarPose.resolve(8, 17, 0, saved));
        assertEquals(new ProfileAvatarPose(0, -16, 16), ProfileAvatarPose.resolve(0, -17, 17, saved));
    }

    @Test
    void directionsAreZeroToSeven() {
        assertTrue(ProfileAvatarPose.isDirection(0));
        assertTrue(ProfileAvatarPose.isDirection(7));
        assertFalse(ProfileAvatarPose.isDirection(-1));
        assertFalse(ProfileAvatarPose.isDirection(8));
    }

    @Test
    void theRoomUnitInfoEndsWithThePoseAfterTheBorder() {
        UserInfo info = mock(UserInfo.class);
        when(info.getLook()).thenReturn("hd-180-1");
        when(info.getGender()).thenReturn(UserGender.M);
        when(info.getMotto()).thenReturn("hi");
        when(info.getInfostandBorder()).thenReturn(12);
        when(info.getProfileAvatarPose()).thenReturn(new ProfileAvatarPose(6, -4, 9));
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getUserStats()).thenReturn(mock(UserStats.class));

        ByteBuf payload = new RoomUserDataComposer(user).compose().get();
        payload.skipBytes(4);

        assertEquals(Outgoing.RoomUserDataComposer, payload.readUnsignedShort());
        assertEquals(-1, payload.readInt());
        assertEquals("hd-180-1", string(payload));
        assertEquals("M", string(payload));
        assertEquals("hi", string(payload));
        for (int i = 0; i < 5; i++) payload.readInt();
        for (int i = 0; i < 7; i++) string(payload);
        assertEquals(12, payload.readInt());
        assertEquals(6, payload.readInt());
        assertEquals(-4, payload.readInt());
        assertEquals(9, payload.readInt());
        assertFalse(payload.isReadable());
    }

    private static String string(ByteBuf buffer) {
        byte[] bytes = new byte[buffer.readUnsignedShort()];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
