package polaris.hotel.rooms;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Test;
import polaris.messages.ServerMessage;
import polaris.plugin.Event;
import polaris.plugin.PluginManager;

/** The room hears that rights were removed only when someone actually held them. */
class RoomRightsRemovalTest {

    @Test
    void removingRightsNobodyHeldSendsNothing() {
        Room room = mock(Room.class);
        PluginManager plugins = mock(PluginManager.class);
        when(room.pluginManager()).thenReturn(plugins);
        when(plugins.fireEvent(any(Event.class))).thenAnswer(returnsFirstArg());
        RoomRightsManager manager = new RoomRightsManager(
                room, IntArrayList.of(2), new Int2ObjectOpenHashMap<>(), new Int2IntOpenHashMap());

        manager.removeRights(3);

        verify(room, never()).sendComposer(any(ServerMessage.class));
    }
}
