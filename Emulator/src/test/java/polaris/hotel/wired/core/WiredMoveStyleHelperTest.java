package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.WiredFurniMoveStyleComposer;

class WiredMoveStyleHelperTest {

    @Test
    void onlyCapableClientsReceiveTheStyleHint() {
        Room room = mock(Room.class);
        User capable = user(true);
        User legacy = user(false);
        when(room.getUsers()).thenReturn(List.of(capable, legacy));

        WiredMoveStyleHelper.broadcast(room, List.of(101), WiredMoveStyleHelper.STYLE_DROP, 100);

        verify(capable.getClient()).sendResponse(any(ServerMessage.class));
        verify(legacy.getClient(), never()).sendResponse(any(ServerMessage.class));
    }

    @Test
    void linearStyleAndEmptyTargetsSendNothing() {
        Room room = mock(Room.class);
        User capable = user(true);
        when(room.getUsers()).thenReturn(List.of(capable));

        WiredMoveStyleHelper.broadcast(room, List.of(101), WiredMoveStyleHelper.STYLE_LINEAR, 100);
        WiredMoveStyleHelper.broadcast(room, List.of(), WiredMoveStyleHelper.STYLE_DROP, 100);
        WiredMoveStyleHelper.broadcast(null, List.of(101), WiredMoveStyleHelper.STYLE_DROP, 100);

        verify(capable.getClient(), never()).sendResponse(any(ServerMessage.class));
    }

    @Test
    void hintsRaisedDuringOneFiringGoOutAsOnePacketWhenItFinishes() {
        Room room = mock(Room.class);
        User capable = user(true);
        when(room.getUsers()).thenReturn(List.of(capable));

        WiredMoveCarryHelper.beginMovementCollection();
        for (int id = 1; id <= 300; id++) {
            WiredMoveStyleHelper.broadcast(room, List.of(id), WiredMoveStyleHelper.STYLE_JUMP, 80);
        }
        verify(capable.getClient(), never()).sendResponse(any(ServerMessage.class));

        WiredMoveCarryHelper.finishMovementCollection();
        verify(capable.getClient(), times(1)).sendResponse(any(ServerMessage.class));
    }

    @Test
    void avatarJumpsOnlyReachClientsThatDrawThem() {
        Room room = mock(Room.class);
        User moveStyleOnly = user(true);
        User trajectory = user(true, true);
        when(room.getUsers()).thenReturn(List.of(moveStyleOnly, trajectory));

        WiredMoveStyleHelper.sendUnitsNow(room, List.of(5), WiredMoveStyleHelper.STYLE_JUMP, 80);
        WiredMoveStyleHelper.broadcastUnits(room, List.of(6), WiredMoveStyleHelper.STYLE_JUMP, 80);

        verify(moveStyleOnly.getClient(), never()).sendResponse(any(ServerMessage.class));
        verify(trajectory.getClient(), times(2)).sendResponse(any(ServerMessage.class));
    }

    @Test
    void anOvershootAloneIsWorthAHintAndSplitsTheBatch() {
        Room room = mock(Room.class);
        User capable = user(true);
        when(room.getUsers()).thenReturn(List.of(capable));

        WiredMoveCarryHelper.beginMovementCollection();
        WiredMoveStyleHelper.broadcast(room, List.of(1), WiredMoveStyleHelper.STYLE_LINEAR, 0, 3);
        WiredMoveStyleHelper.broadcast(room, List.of(2), WiredMoveStyleHelper.STYLE_LINEAR, 0, 3);
        WiredMoveStyleHelper.broadcast(room, List.of(3), WiredMoveStyleHelper.STYLE_JUMP, 120, 3);
        WiredMoveStyleHelper.broadcast(room, List.of(4), WiredMoveStyleHelper.STYLE_JUMP, 120, 0);
        WiredMoveCarryHelper.finishMovementCollection();

        verify(capable.getClient(), times(3)).sendResponse(any(ServerMessage.class));
    }

    @Test
    void theHintCarriesTheOvershootAndKindAfterTheOriginalFields() {
        ServerMessage message = new WiredFurniMoveStyleComposer(
                        List.of(9, 10), WiredMoveStyleHelper.STYLE_JUMP, 5000, -99, WiredMoveStyleHelper.KIND_UNIT)
                .compose();
        ByteBuf buffer = message.get();
        try {
            buffer.readInt();
            assertEquals(5110, buffer.readShort());
            assertEquals(2, buffer.readInt());
            assertEquals(9, buffer.readInt());
            assertEquals(10, buffer.readInt());
            assertEquals(WiredMoveStyleHelper.STYLE_JUMP, buffer.readInt());
            assertEquals(1000, buffer.readInt());
            assertEquals(-64, buffer.readInt());
            assertEquals(WiredMoveStyleHelper.KIND_UNIT, buffer.readInt());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static User user(boolean capable) {
        return user(capable, false);
    }

    private static User user(boolean capable, boolean trajectory) {
        User user = mock(User.class);
        GameClient client = mock(GameClient.class);
        when(user.getClient()).thenReturn(client);
        when(client.supportsWiredFeature(
                        GameClient.WIRED_FEATURE_PROTOCOL_VERSION, GameClient.WIRED_FEATURE_MOVE_STYLE))
                .thenReturn(capable);
        when(client.supportsWiredFeature(
                        GameClient.WIRED_FEATURE_PROTOCOL_VERSION, GameClient.WIRED_FEATURE_TRAJECTORY))
                .thenReturn(trajectory);
        return user;
    }
}
