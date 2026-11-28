package polaris.messages.incoming.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.profile.ProfileAvatarPose;
import polaris.messages.ClientMessage;
import polaris.messages.outgoing.MessageComposer;

/** The avatar's pose in the profile picture travels after the profile accent of the infostand background request. */
class ChangeInfostandBgPoseTest {
    private static final ProfileAvatarPose SAVED = new ProfileAvatarPose(4, 3, -5);

    @Test
    void savesADirectionAndOffsetInsideTheBox() throws Exception {
        assertEquals(new ProfileAvatarPose(6, -12, 9), handle(7, 6, -12, 9));
    }

    @Test
    void clampsAnOffsetBeyondTheBoxToItsEdge() throws Exception {
        assertEquals(new ProfileAvatarPose(0, 16, -16), handle(7, 0, 400, -99));
    }

    @Test
    void refusesADirectionOutsideZeroToSevenAndKeepsTheSavedOne() throws Exception {
        assertEquals(new ProfileAvatarPose(SAVED.direction(), 1, 2), handle(7, 8, 1, 2));
        assertEquals(new ProfileAvatarPose(SAVED.direction(), 1, 2), handle(7, -7, 1, 2));
    }

    @Test
    void keepsTheSavedPoseWhenTheClientSendsKeep() throws Exception {
        UserInfo info = run(packet(1, 2, 3, 4, 5, -1, -1, ProfileAvatarPose.KEEP, 10, 10));

        // Nothing else changed either, so nothing is written or sent.
        verify(info, never()).setProfileAvatarPose(any());
        verify(info, never()).run();
    }

    @Test
    void keepsTheSavedPoseForAnOlderClientThatSendsNoPose() throws Exception {
        UserInfo info = run(packet(9, 2, 3, 4, 5, -1, -1));

        assertEquals(SAVED, savedPose(info));
    }

    private static ProfileAvatarPose handle(int background, int direction, int offsetX, int offsetY) throws Exception {
        return savedPose(run(packet(background, 2, 3, 4, 5, -1, -1, direction, offsetX, offsetY)));
    }

    private static ProfileAvatarPose savedPose(UserInfo info) {
        ArgumentCaptor<ProfileAvatarPose> pose = ArgumentCaptor.forClass(ProfileAvatarPose.class);
        verify(info).setProfileAvatarPose(pose.capture());
        verify(info).run();
        return pose.getValue();
    }

    private static UserInfo run(ClientMessage packet) throws Exception {
        UserInfo info = mock(UserInfo.class);
        when(info.getInfostandBg()).thenReturn(1);
        when(info.getInfostandStand()).thenReturn(2);
        when(info.getInfostandOverlay()).thenReturn(3);
        when(info.getInfostandCardBg()).thenReturn(4);
        when(info.getInfostandBorder()).thenReturn(5);
        when(info.getProfileAvatarPose()).thenReturn(SAVED);

        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(info);
        GameClient client = mock(GameClient.class);
        when(client.getUser()).thenReturn(user);

        ChangeInfostandBgEvent event = new ChangeInfostandBgEvent();
        event.client = client;
        event.packet = packet;

        // No game environment: every id is allowed, so only the pose rules decide.
        try (MockedStatic<Emulator> ignored = mockStatic(Emulator.class)) {
            event.handle();
        }

        verify(client, org.mockito.Mockito.atMost(1)).sendResponse(any(MessageComposer.class));
        return info;
    }

    private static ClientMessage packet(int... values) {
        ByteBuf buffer = Unpooled.buffer();
        for (int value : values) buffer.writeInt(value);
        return new ClientMessage(0, buffer);
    }
}
