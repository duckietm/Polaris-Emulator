package polaris.hotel.polls;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;

/** :update_polls cleared the polls while poll packets read them; a failed query left no polls. */
class PollReloadTest {

    @Test
    void aReloadThatCannotReachTheDatabaseKeepsThePolls() throws Exception {
        PollManager manager = mock(PollManager.class, CALLS_REAL_METHODS);
        Poll poll = mock(Poll.class);
        Map<Integer, Poll> polls = new HashMap<>();
        polls.put(3, poll);
        Field field = PollManager.class.getDeclaredField("activePolls");
        field.setAccessible(true);
        field.set(manager, polls);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenThrow(new SQLException("down"));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            manager.loadPolls();
        }

        assertSame(poll, manager.getPoll(3));
    }
}
