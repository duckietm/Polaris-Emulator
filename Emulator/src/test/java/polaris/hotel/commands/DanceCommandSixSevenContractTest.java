package polaris.hotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.TextsManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUserAction;
import polaris.hotel.users.DanceType;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.outgoing.rooms.users.RoomUserActionComposer;

class DanceCommandSixSevenContractTest {

    @Test
    void sixSevenIsAction67() {
        assertEquals(67, RoomUserAction.SIX_SEVEN.getAction());
        assertEquals(RoomUserAction.SIX_SEVEN, RoomUserAction.fromValue(67));
    }

    @Test
    void danceSixSevenSendsTheActionInsteadOfTheBoundsError() throws Exception {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        Room room = mock(Room.class);
        RoomUnit unit = mock(RoomUnit.class);
        GameClient client = mock(GameClient.class);
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenAnswer(i -> i.getArgument(0));
        when(client.getUser()).thenReturn(user);
        when(user.getUserInfo()).thenReturn(info);
        when(user.getRoomUnit()).thenReturn(unit);
        when(info.getCurrentRoom()).thenReturn(room);
        when(unit.getDanceType()).thenReturn(DanceType.NONE);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedConstruction<RoomUserActionComposer> actions = mockConstruction(RoomUserActionComposer.class)) {
            emulator.when(Emulator::getTexts).thenReturn(texts);

            new DanceCommand().handle(client, new String[] {"dance", "67"});

            assertEquals(1, actions.constructed().size(), "one six-seven action is sent to the room");
            verify(room).sendComposer(any());
            verify(user, never()).whisper("commands.error.cmd_dance.outside_bounds", RoomChatMessageBubbles.ALERT);
        }
    }

    @Test
    void anOtherNumberAboveFourIsStillOutOfBounds() throws Exception {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        GameClient client = mock(GameClient.class);
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenAnswer(i -> i.getArgument(0));
        when(client.getUser()).thenReturn(user);
        when(user.getUserInfo()).thenReturn(info);
        when(info.getCurrentRoom()).thenReturn(mock(Room.class));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getTexts).thenReturn(texts);

            new DanceCommand().handle(client, new String[] {"dance", "5"});

            verify(user).whisper("commands.error.cmd_dance.outside_bounds", RoomChatMessageBubbles.ALERT);
        }
    }
}
