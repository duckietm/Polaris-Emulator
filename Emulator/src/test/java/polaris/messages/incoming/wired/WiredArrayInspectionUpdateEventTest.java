package polaris.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.variables.RoomArrayVariableManager;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.wired.WiredVariableChangeOrigin;
import polaris.hotel.wired.arrays.WiredArrayDefinition;
import polaris.hotel.wired.arrays.WiredArrayMutationResult;
import polaris.hotel.wired.arrays.WiredArrayNumericOperation;
import polaris.hotel.wired.arrays.WiredArrayVariableDefinition;
import polaris.hotel.wired.arrays.WiredArrayVariableType;
import polaris.hotel.wired.arrays.WiredCreatorToolsArrayInspection;
import polaris.hotel.wired.arrays.WiredVariableDefinitionData;
import polaris.hotel.wired.core.WiredEvent;
import polaris.hotel.wired.core.WiredManager;
import polaris.messages.ClientMessage;

class WiredArrayInspectionUpdateEventTest {
    @Test
    void inspectorArrayMutationEmitsCreatorToolsOriginAndLongValuesAfterCommit() throws Exception {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(81);
        User editor = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(editor.getUserInfo()).thenReturn(info);
        when(info.getCurrentRoom()).thenReturn(room);
        when(room.canModifyWired(editor)).thenReturn(true);
        RoomArrayVariableManager arrays = mock(RoomArrayVariableManager.class);
        when(room.getArrayVariableManager()).thenReturn(arrays);
        WiredVariableDefinitionData schema = new WiredVariableDefinitionData();
        schema.valueShape = "array";
        schema.maxEntries = 64;
        WiredArrayDefinition array = WiredArrayDefinition.fromData(schema, 64);
        WiredArrayVariableDefinition definition = mock(WiredArrayVariableDefinition.class);
        when(definition.getId()).thenReturn(12);
        when(definition.getArrayVariableType()).thenReturn(WiredArrayVariableType.ROOM);
        when(definition.getArrayDefinition()).thenReturn(array);
        when(definition.isArrayWritable()).thenReturn(true);
        when(arrays.hasValue(definition, 81)).thenReturn(true);
        when(arrays.mutateField(definition, 81, 0, 1, WiredArrayNumericOperation.ASSIGN, Long.MAX_VALUE))
                .thenReturn(new RoomArrayVariableManager.FieldMutationOutcome(
                        WiredArrayMutationResult.SUCCESS, null, 7L, Long.MAX_VALUE, true));
        WiredArrayInspectionUpdateEvent handler = new WiredArrayInspectionUpdateEvent();
        handler.client = mock(GameClient.class);
        when(handler.client.getUser()).thenReturn(editor);
        byte[] value = String.valueOf(Long.MAX_VALUE).getBytes(StandardCharsets.UTF_8);
        var buffer = Unpooled.buffer()
                .writeInt(WiredArrayVariableType.ROOM.code())
                .writeInt(81)
                .writeInt(12)
                .writeInt(0)
                .writeInt(1)
                .writeShort(value.length)
                .writeBytes(value)
                .writeInt(0)
                .writeInt(20);
        handler.packet = new ClientMessage(10035, buffer);
        try (var resolver = mockStatic(WiredArrayCreatorToolsSupport.class);
                var manager = mockStatic(WiredManager.class);
                var inspection = mockStatic(WiredCreatorToolsArrayInspection.class)) {
            resolver.when(() -> WiredArrayCreatorToolsSupport.resolve(room, WiredArrayVariableType.ROOM.code(), 81, 12))
                    .thenReturn(new WiredArrayCreatorToolsSupport.Resolved(definition, "room", 81, 81, null, null));
            manager.when(() -> WiredManager.handleEvent(any(WiredEvent.class))).thenAnswer(invocation -> {
                WiredEvent event = invocation.getArgument(0);
                assertEquals(WiredVariableChangeOrigin.CREATOR_TOOLS, event.getVariableChangeOrigin());
                assertEquals(7L, event.getArrayChange().oldValue());
                assertEquals(Long.MAX_VALUE, event.getArrayChange().newValue());
                verify(arrays).mutateField(definition, 81, 0, 1, WiredArrayNumericOperation.ASSIGN, Long.MAX_VALUE);
                return true;
            });
            handler.handle();
            manager.verify(() -> WiredManager.handleEvent(any(WiredEvent.class)));
        } finally {
            buffer.release();
        }
    }
}
