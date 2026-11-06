package polaris.hotel.wired.arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.items.interactions.wired.conditions.WiredConditionCheckArray;
import polaris.hotel.items.interactions.wired.effects.WiredEffectModifyArray;
import polaris.hotel.items.interactions.wired.extra.WiredExtraArrayCaptureVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraContextVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomSpecialTypes;
import polaris.messages.incoming.wired.WiredSaveException;

class WiredArrayBoxSaveDataTest {
    @Test
    void modifyArrayRejectsIndexAtCapacityBoundary() {
        Fixture fixture = fixture();
        WiredEffectModifyArray effect = new WiredEffectModifyArray(10, 1, null, "", 0, 0);
        effect.setRoomId(fixture.room().getId());
        WiredSettings settings = settings(
                fixture.room(),
                new int[] {WiredArrayVariableType.ROOM.code(), WiredArrayStructuralOperation.SET_ENTRY.code(), 0},
                """
                {"variableItemId":99,"firstIndex":{"mode":0,"value":2},
                 "fieldInputs":{"1":{"mode":0,"value":"5"}}}
                """);

        assertThrows(WiredSaveException.class, () -> effect.saveData(settings, null));
    }

    @Test
    void checkArrayRejectsIndexAtCapacityBoundary() {
        Fixture fixture = fixture();
        WiredConditionCheckArray condition = new WiredConditionCheckArray(11, 1, null, "", 0, 0);
        condition.setRoomId(fixture.room().getId());
        WiredSettings settings = settings(
                fixture.room(), new int[] {WiredArrayVariableType.ROOM.code(), 0, 0, 1, 0, 1, 2, 0, 2, 0}, """
                {"variableItemId":99,"index":{"mode":0,"value":2},
                 "criteria":[{"fieldId":1,"comparison":2,"reference":{"mode":0,"value":"5"}}]}
                """);

        assertFalse(condition.saveData(settings));
    }

    @Test
    void arrayCapturerRejectsMalformedCapturePath() {
        Fixture fixture = fixture();
        WiredExtraArrayCaptureVariable capture = new WiredExtraArrayCaptureVariable(12, 1, null, "", 0, 0);
        capture.setRoomId(fixture.room().getId());
        WiredSettings settings =
                settings(fixture.room(), new int[] {WiredArrayVariableType.ROOM.code(), 0, 1, 0, 0}, """
                {"variableItemId":99,"contextVariableItemId":100,
                 "criteria":[{"fieldId":1,"comparison":2,
                 "reference":{"mode":1,"variableType":3,"capturePath":"../bad"}}]}
                """);

        assertThrows(WiredSaveException.class, () -> capture.saveData(settings, null));
    }

    private static Fixture fixture() {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        WiredExtraRoomVariable array = mock(WiredExtraRoomVariable.class);
        WiredExtraContextVariable context = mock(WiredExtraContextVariable.class);
        WiredArrayDefinition definition = simpleDefinition();

        when(room.getId()).thenReturn(7);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtra(99)).thenReturn(array);
        when(specialTypes.getExtra(100)).thenReturn(context);
        when(specialTypes.getExtras(0, 0)).thenReturn(Set.of());
        when(array.getArrayVariableType()).thenReturn(WiredArrayVariableType.ROOM);
        when(array.getArrayDefinition()).thenReturn(definition);
        when(array.isArray()).thenReturn(true);
        when(context.getVariableName()).thenReturn("capture");
        when(context.isArray()).thenReturn(false);
        when(context.hasValue()).thenReturn(true);
        return new Fixture(room);
    }

    private static WiredArrayDefinition simpleDefinition() {
        WiredVariableDefinitionData data = new WiredVariableDefinitionData();
        data.valueShape = "array";
        data.arrayFormat = "simple";
        data.arrayMode = "list";
        data.maxEntries = 2;
        return WiredArrayDefinition.fromData(data, 2);
    }

    private static WiredSettings settings(Room room, int[] params, String json) {
        WiredSettings settings = new WiredSettings(params, json, new int[0], -1, 0);
        settings.setRoom(room);
        return settings;
    }

    private record Fixture(Room room) {}
}
