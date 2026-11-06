package polaris.hotel.users.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;

/**
 * :massbadge and :roombadge inserted one row per user synchronously on the chat thread, never checked the code, and
 * showed the badge even when its insert failed - a ghost badge gone at the next login.
 */
class BadgeGrantTest {

    private static User user(int id, BadgesComponent badges) {
        User user = mock(User.class, RETURNS_DEEP_STUBS);
        when(user.getUserInfo().getId()).thenReturn(id);
        when(user.getInventory().getBadgesComponent()).thenReturn(badges);
        return user;
    }

    @Test
    void aBadgeCodeFitsTheColumn() {
        assertTrue(BadgesComponent.isValidBadgeCode("ADM"));
        assertTrue(BadgesComponent.isValidBadgeCode("X".repeat(32)));
        assertFalse(BadgesComponent.isValidBadgeCode("X".repeat(33)));
        assertFalse(BadgesComponent.isValidBadgeCode("bad code"));
        assertFalse(BadgesComponent.isValidBadgeCode("../x"));
        assertFalse(BadgesComponent.isValidBadgeCode(""));
        assertFalse(BadgesComponent.isValidBadgeCode(null));
    }

    @Test
    void onlyAUserWhoseRowWasWrittenGetsTheBadge() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet keys = mock(ResultSet.class);
        when(keys.next()).thenReturn(true);
        when(keys.getInt(1)).thenReturn(55);
        when(statement.getGeneratedKeys()).thenReturn(keys);
        // The second user's insert fails; the first and the third go through.
        when(statement.executeUpdate())
                .thenReturn(1)
                .thenThrow(new SQLException("too long"))
                .thenReturn(1);
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString(), anyInt())).thenReturn(statement);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenReturn(connection);

        BadgesComponent first = mock(BadgesComponent.class);
        BadgesComponent second = mock(BadgesComponent.class);
        BadgesComponent third = mock(BadgesComponent.class);
        List<User> granted = new ArrayList<>();

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            int count = BadgesComponent.grantBadgeNow(
                    List.of(user(1, first), user(2, second), user(3, third)),
                    "EVT",
                    (user, badge) -> granted.add(user));

            assertEquals(2, count);
        }

        verify(first).addBadge(any(UserBadge.class));
        verify(second, never()).addBadge(any(UserBadge.class));
        verify(third).addBadge(any(UserBadge.class));
        assertEquals(2, granted.size());
    }

    @Test
    void theCommandsCheckTheCodeAndLeaveTheChatThread() throws Exception {
        for (String command : List.of("MassBadgeCommand", "RoomBadgeCommand")) {
            String source = Files.readString(Path.of("src/main/java/polaris/hotel/commands/" + command + ".java"));

            assertTrue(source.contains("BadgesComponent.isValidBadgeCode(badge)"), command);
            assertTrue(source.contains("BadgesComponent.grantBadge("), command);
            assertFalse(source.contains("BadgesComponent.createBadge("), command);
        }
    }
}
