package polaris.hotel.navigation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.database.Database;
import polaris.hotel.GameEnvironment;
import polaris.hotel.rooms.Room;

/**
 * A staff pick category id that pointed at a missing or hidden public category threw a NullPointerException, which
 * stopped the start or left a reloaded navigator half filled.
 */
class NavigatorStaffPicksTest {

    @Test
    void aMissingStaffPickCategoryIsLoggedNotThrown() {
        NavigatorManager manager = new NavigatorManager();
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        GameEnvironment environment = mock(GameEnvironment.class, RETURNS_DEEP_STUBS);
        when(environment.getRoomManager().getRoomsStaffPromoted())
                .thenReturn(new ArrayList<>(List.of(mock(Room.class))));
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getInt("hotel.navigator.staffpicks.categoryid")).thenReturn(99);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            emulator.when(Emulator::getConfig).thenReturn(config);

            assertDoesNotThrow(manager::loadNavigator);
        }
    }
}
