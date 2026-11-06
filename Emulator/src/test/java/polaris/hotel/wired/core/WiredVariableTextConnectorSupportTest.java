package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.wired.extra.WiredExtraVariableTextConnector;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomSpecialTypes;

class WiredVariableTextConnectorSupportTest {

    private static WiredExtraVariableTextConnector connector(int id, double z, Map<Integer, String> mappings) {
        WiredExtraVariableTextConnector connector = mock(WiredExtraVariableTextConnector.class);
        when(connector.getId()).thenReturn(id);
        when(connector.getZ()).thenReturn(z);
        when(connector.getMappings()).thenReturn(mappings);
        when(connector.appliesToField(0)).thenReturn(true);
        // The real connector resolves through its own table and falls back to the plain number, which is
        // what makes the first connector naming a value win in toText.
        when(connector.resolveText(any(Integer.class)))
                .thenAnswer(invocation -> resolveText(mappings, invocation.getArgument(0)));
        when(connector.resolveText(anyLong()))
                .thenAnswer(invocation -> resolveText(mappings, invocation.getArgument(0)));
        return connector;
    }

    private static String resolveText(Map<Integer, String> mappings, Number value) {
        String mapped = mappings.get(value.intValue());
        return (mapped != null) ? mapped : String.valueOf(value);
    }

    private static Room roomWithDefinition(int definitionItemId, Set<InteractionWiredExtra> extrasOnTile) {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        InteractionWiredExtra definition = mock(InteractionWiredExtra.class);

        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(definition.getX()).thenReturn((short) 4);
        when(definition.getY()).thenReturn((short) 5);
        when(specialTypes.getExtra(definitionItemId)).thenReturn(definition);
        when(specialTypes.getExtras((short) 4, (short) 5)).thenReturn(extrasOnTile);
        return room;
    }

    @Test
    void theTableFollowsStackOrderAndTheLowestConnectorNamesAValueFirst() {
        Map<Integer, String> lower = new LinkedHashMap<>();
        lower.put(1, "Sword");
        lower.put(2, "Shield");
        Map<Integer, String> upper = new LinkedHashMap<>();
        upper.put(2, "Buckler");
        upper.put(3, "Bow");

        Room room = roomWithDefinition(42, Set.of(connector(900, 1.0, upper), connector(800, 0.5, lower)));

        Map<Integer, String> mappings = WiredVariableTextConnectorSupport.mappings(room, 42);

        assertEquals(List.of(1, 2, 3), List.copyOf(mappings.keySet()));
        assertEquals("Shield", mappings.get(2), "the connector lower in the stack wins, as toText resolves it");
        assertEquals("Bow", mappings.get(3));
        assertEquals("Shield", WiredVariableTextConnectorSupport.toText(room, 42, 2));
    }

    @Test
    void aDefinitionWithoutAConnectorHasAnEmptyTable() {
        Room room = roomWithDefinition(42, Set.of(mock(InteractionWiredExtra.class)));

        assertTrue(WiredVariableTextConnectorSupport.mappings(room, 42).isEmpty());
        assertTrue(WiredVariableTextConnectorSupport.mappings(null, 42).isEmpty());
        assertTrue(WiredVariableTextConnectorSupport.mappings(room, 0).isEmpty());
    }
}
