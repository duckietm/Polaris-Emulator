package polaris.messages.incoming.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.inventory.WardrobeComponent;

/** Wardrobe slots run 1 to 10, the look is a real figure, and a slot holds one item. */
class SaveWardrobeEventTest {

    private static final String LOOK = "hr-115-42.hd-195-19.ch-3030-82.lg-275-1408";

    @Test
    void onlyTheClientsSlotsAndWellFormedFiguresAreAccepted() {
        assertTrue(SaveWardrobeEvent.isAcceptable(1, LOOK, "M"));
        assertTrue(SaveWardrobeEvent.isAcceptable(10, LOOK, "F"));

        assertFalse(SaveWardrobeEvent.isAcceptable(0, LOOK, "M"), "slot 0 is not offered");
        assertFalse(SaveWardrobeEvent.isAcceptable(11, LOOK, "M"));
        assertFalse(SaveWardrobeEvent.isAcceptable(1, "<script>", "M"), "not a figure");
        assertFalse(SaveWardrobeEvent.isAcceptable(1, "", "M"));
        assertFalse(SaveWardrobeEvent.isAcceptable(1, LOOK, "X"));
    }

    @Test
    void savingASlotTwiceUpdatesTheSameItem() throws Exception {
        Database database = mock(Database.class);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(database.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenThrow(new SQLException("no database in this test"));
        User user = mock(User.class, RETURNS_DEEP_STUBS);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);
            WardrobeComponent wardrobe = new WardrobeComponent(user);

            WardrobeComponent.WardrobeItem first = wardrobe.saveLook(user, 3, LOOK, UserGender.M);
            WardrobeComponent.WardrobeItem second = wardrobe.saveLook(user, 3, "hd-180-1", UserGender.F);

            assertSame(first, second);
            assertEquals(1, wardrobe.getLooks().size());
            assertEquals("hd-180-1", second.getLook());
            assertEquals(UserGender.F, second.getGender());
        }
    }
}
