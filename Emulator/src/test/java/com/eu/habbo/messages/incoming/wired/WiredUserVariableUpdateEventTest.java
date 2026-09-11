package com.eu.habbo.messages.incoming.wired;

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

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.wired.WiredVariableChangeOrigin;
import com.eu.habbo.habbohotel.wired.core.WiredInternalVariableSupport;
import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WiredUserVariableUpdateEventTest {
    @Test
    void legacyFourIntegersStillUpdateCustomVariablesWithCreatorOrigin() throws Exception {
        Fixture fixture = new Fixture();
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
        Habbo target = mock(Habbo.class);
        RoomUnit unit = mock(RoomUnit.class);
        when(target.getRoomUnit()).thenReturn(unit);
        when(fixture.room.getHabbo(9)).thenReturn(target);

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
        final Habbo editor = mock(Habbo.class);
        final WiredUserVariableUpdateEvent handler = new WiredUserVariableUpdateEvent();

        Fixture() {
            HabboInfo info = mock(HabboInfo.class);
            handler.client = mock(GameClient.class);
            when(handler.client.getHabbo()).thenReturn(editor);
            when(editor.getHabboInfo()).thenReturn(info);
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
