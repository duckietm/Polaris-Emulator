package polaris.hotel.guilds;

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

/** A reload of the badge parts cleared them first, and a failed query left guild creation without any. */
class GuildPartsReloadTest {

    @Test
    void aReloadThatCannotReachTheDatabaseKeepsTheParts() throws Exception {
        GuildManager manager = mock(GuildManager.class, CALLS_REAL_METHODS);
        GuildPart base = mock(GuildPart.class);
        Map<GuildPartType, Map<Integer, GuildPart>> parts = new HashMap<>();
        for (GuildPartType type : GuildPartType.values()) parts.put(type, new HashMap<>());
        parts.get(GuildPartType.BASE).put(1, base);
        Field field = GuildManager.class.getDeclaredField("guildParts");
        field.setAccessible(true);
        field.set(manager, parts);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenThrow(new SQLException("down"));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            manager.loadGuildParts();
        }

        assertSame(base, manager.getBase(1));
    }
}
