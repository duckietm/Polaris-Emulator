package polaris.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.variables.RoomUserVariableManager;
import polaris.hotel.rooms.variables.RoomVariableManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.wired.WiredVariableChangeOrigin;
import polaris.hotel.wired.core.WiredInternalVariableSupport;
import polaris.messages.ClientMessage;

class WiredUserVariableUpdateEventTest {
    @Test
    void legacyFourIntegersStillUpdateCustomVariablesWithCreatorOrigin() throws Exception {
        Fixture fixture = new Fixture();
        // The user is in the room: an absent user's value is only the definition owner's to change.
        User present = mock(User.class);
        when(present.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        when(fixture.room.getUser(9)).thenReturn(present);
        doAnswer(invocation -> {
                    assertEquals(WiredVariableChangeOrigin.CREATOR_TOOLS, WiredVariableChangeOrigin.current());
                    return true;
                })
                .when(fixture.users)
                .updateVariableValue(9, 12, 34);

        fixture.handle(0, 9, 12, 34, null);

        verify(fixture.users).updateVariableValue(9, 12, 34);
        verify(fixture.users).sendSnapshot(fixture.editor);
    }

    @Test
    void internalUserEditsRequireAltitudeAndAnOccupantOfTheCurrentRoom() throws Exception {
        Fixture fixture = new Fixture();
        User target = mock(User.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(target.getRoomUnit()).thenReturn(unit);
        when(fixture.room.getUser(9)).thenReturn(target);

        try (var internal = mockStatic(WiredInternalVariableSupport.class, CALLS_REAL_METHODS)) {
            internal.when(() -> WiredInternalVariableSupport.writeUserValue(fixture.room, unit, "@altitude", 125))
                    .thenReturn(true);
            internal.clearInvocations();
            clearInvocations(unit);
            fixture.handle(0, 9, 0, 125, "internal:@altitude");
            fixture.handle(0, 10, 0, 125, "internal:@altitude");
            fixture.handle(0, 9, 0, 125, "internal:@effect_id");
            fixture.handle(0, 9, 12, 125, "internal:@altitude");
            internal.verify(() -> WiredInternalVariableSupport.writeUserValue(fixture.room, unit, "@altitude", 125));
        }

        verifyNoMoreInteractions(unit);
        verify(fixture.users, never()).updateVariableValue(anyInt(), anyInt(), anyInt());
    }

    @Test
    void internalRoomEditsAcceptTeamScoresOnlyForTheCurrentRoom() throws Exception {
        Fixture fixture = new Fixture();
        try (var internal = mockStatic(WiredInternalVariableSupport.class, CALLS_REAL_METHODS)) {
            internal.when(() -> WiredInternalVariableSupport.writeRoomValue(fixture.room, "@team_red_score", 42))
                    .thenReturn(true);
            internal.clearInvocations();
            fixture.handle(3, 81, 0, 42, "internal:@teams.red.score");
            fixture.handle(3, 82, 0, 42, "internal:@teams.red.score");
            fixture.handle(3, 81, 0, 42, "internal:@user_count");
            internal.verify(() -> WiredInternalVariableSupport.writeRoomValue(fixture.room, "@team_red_score", 42));
        }
        verify(fixture.variables, never()).updateVariableValue(anyInt(), anyInt());
    }

    @Test
    void unauthorizedAndMalformedTailsCannotMutateValues() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.room.canModifyWired(fixture.editor)).thenReturn(false);
        fixture.handle(0, 9, 12, 34, null);
        when(fixture.room.canModifyWired(fixture.editor)).thenReturn(true);
        fixture.handle(0, 9, 12, 34, "x".repeat(65));
        ByteBuf malformed = Unpooled.buffer()
                .writeInt(0)
                .writeInt(9)
                .writeInt(12)
                .writeInt(34)
                .writeShort(99);
        fixture.handle(malformed);
        verify(fixture.users, never()).updateVariableValue(anyInt(), anyInt(), anyInt());
    }

    private static final class Fixture {
        final Room room = mock(Room.class);
        final RoomUserVariableManager users = mock(RoomUserVariableManager.class);
        final RoomVariableManager variables = mock(RoomVariableManager.class);
        final User editor = mock(User.class);
        final WiredUserVariableUpdateEvent handler = new WiredUserVariableUpdateEvent();

        Fixture() {
            UserInfo info = mock(UserInfo.class);
            handler.client = mock(GameClient.class);
            when(handler.client.getUser()).thenReturn(editor);
            when(editor.getUserInfo()).thenReturn(info);
            when(info.getCurrentRoom()).thenReturn(room);
            when(room.getId()).thenReturn(81);
            when(room.canModifyWired(editor)).thenReturn(true);
            when(room.getUserVariableManager()).thenReturn(users);
            when(room.getRoomVariableManager()).thenReturn(variables);
        }

        void handle(int type, int id, int definitionId, int value, String token) throws Exception {
            ByteBuf buffer = Unpooled.buffer()
                    .writeInt(type)
                    .writeInt(id)
                    .writeInt(definitionId)
                    .writeInt(value);
            if (token != null) {
                byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
                buffer.writeShort(bytes.length).writeBytes(bytes);
            }
            handle(buffer);
        }

        void handle(ByteBuf buffer) throws Exception {
            handler.packet = new ClientMessage(10025, buffer);
            int origin = WiredVariableChangeOrigin.current();
            try {
                handler.handle();
                assertEquals(origin, WiredVariableChangeOrigin.current());
            } finally {
                buffer.release();
            }
        }
    }
}
