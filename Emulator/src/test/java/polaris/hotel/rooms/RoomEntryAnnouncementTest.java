package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInventory;
import polaris.hotel.users.UserStats;
import polaris.hotel.users.inventory.EffectsComponent;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;
import polaris.messages.outgoing.rooms.users.RoomUsersComposer;
import polaris.plugin.PluginManager;

/** Observed on AIR: the entering user gets its own Users entry and status once, not again from the broadcast. */
class RoomEntryAnnouncementTest {

    @Test
    void theNewcomerIsAnnouncedToTheOthersAndSeesItselfOnce() throws Exception {
        User entrant = user();
        User present = user();
        Room room = mock(Room.class);
        Field lock = Room.class.getField("roomUnitLock");
        lock.setAccessible(true);
        lock.set(room, new Object());
        ConcurrentHashMap<Integer, User> current = new ConcurrentHashMap<>(Map.of(1, entrant, 2, present));
        when(room.getCurrentUsers()).thenReturn(current);
        when(room.getUsers()).thenReturn(List.of(entrant, present));
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(any(), anyBoolean())).thenReturn(false);

        RoomManager manager = new RoomManager(false);
        Method announce = RoomManager.class.getDeclaredMethod("announceEntry", User.class, Room.class);
        announce.setAccessible(true);
        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedConstruction<RoomUsersComposer> users = mockConstruction(RoomUsersComposer.class);
                MockedConstruction<RoomUserStatusComposer> statuses = mockConstruction(RoomUserStatusComposer.class)) {
            emulator.when(Emulator::getPluginManager).thenReturn(plugins);

            announce.invoke(manager, entrant, room);

            assertEquals(2, users.constructed().size(), "one entry for the others, one batch for the newcomer");
            assertEquals(2, statuses.constructed().size());
        }

        verify(entrant.getClient(), never()).sendResponse(any(ServerMessage.class));
        verify(entrant.getClient(), times(2)).sendResponse(any(MessageComposer.class));
        verify(present.getClient(), times(2)).sendResponse((ServerMessage) any());
    }

    private static User user() {
        User user = mock(User.class);
        when(user.getClient()).thenReturn(mock(GameClient.class));
        when(user.getRoomUnit()).thenReturn(mock(RoomUnit.class));
        when(user.getUserStats()).thenReturn(mock(UserStats.class));
        UserInventory inventory = mock(UserInventory.class);
        when(inventory.getEffectsComponent()).thenReturn(mock(EffectsComponent.class));
        when(user.getInventory()).thenReturn(inventory);
        return user;
    }
}
