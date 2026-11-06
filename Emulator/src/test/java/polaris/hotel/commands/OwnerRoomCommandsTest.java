package polaris.hotel.commands;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.TextsManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/**
 * A command permission of value 2 passes for anyone with rights in the room, as it always has. :setmax and
 * :roommute change the room itself, so a friend given rights could make it unreachable or silence everyone.
 */
class OwnerRoomCommandsTest {
    private MockedStatic<Emulator> emulator;
    private final User user = mock(User.class);
    private final Room room = mock(Room.class);
    private final GameClient client = mock(GameClient.class);

    @BeforeEach
    void setUp() {
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenAnswer(i -> i.getArgument(0));
        this.emulator = mockStatic(Emulator.class);
        this.emulator.when(Emulator::getTexts).thenReturn(texts);

        UserInfo info = mock(UserInfo.class);
        when(this.user.getUserInfo()).thenReturn(info);
        when(info.getCurrentRoom()).thenReturn(this.room);
        when(this.client.getUser()).thenReturn(this.user);
    }

    @AfterEach
    void tearDown() {
        this.emulator.close();
    }

    @Test
    void someoneWithRightsCannotChangeTheRoomLimit() throws Exception {
        when(this.room.isOwner(this.user)).thenReturn(false);

        new SetMaxCommand().handle(this.client, new String[] {"setmax", "50"});

        verify(this.room, never()).setUsersMax(anyInt());
        verify(this.user).whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
    }

    @Test
    void theOwnerChangesTheRoomLimit() throws Exception {
        when(this.room.isOwner(this.user)).thenReturn(true);

        new SetMaxCommand().handle(this.client, new String[] {"setmax", "50"});

        verify(this.room).setUsersMax(50);
    }

    @Test
    void someoneWithRightsCannotMuteTheRoom() throws Exception {
        when(this.room.isOwner(this.user)).thenReturn(false);

        new RoomMuteCommand().handle(this.client, new String[] {"roommute"});

        verify(this.room, never()).setMuted(eq(true));
        verify(this.room, never()).setMuted(eq(false));
        verify(this.user).whisperLocalized("generic.cannot_do_that", RoomChatMessageBubbles.ALERT);
    }

    @Test
    void theOwnerMutesTheRoom() throws Exception {
        when(this.room.isOwner(this.user)).thenReturn(true);
        when(this.room.isMuted()).thenReturn(false);

        new RoomMuteCommand().handle(this.client, new String[] {"roommute"});

        verify(this.room).setMuted(true);
    }
}
