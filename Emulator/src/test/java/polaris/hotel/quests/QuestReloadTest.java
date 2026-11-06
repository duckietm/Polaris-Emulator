package polaris.hotel.quests;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.database.Database;

/** A quest reload cleared the definitions before querying them, and a failed query left no quests at all. */
class QuestReloadTest {

    @Test
    void aReloadThatCannotReachTheDatabaseKeepsTheQuests() throws Exception {
        QuestManager manager = new QuestManager(false) {};
        Quest chat = new Quest(
                1,
                "chat",
                "chat_1",
                "",
                1,
                QuestGoalType.TALK_IN_ROOM,
                "",
                2,
                Quest.REWARD_DUCKETS,
                20,
                "",
                true,
                false,
                0,
                0,
                "",
                "1");
        manager.register(chat);
        Database database = mock(Database.class, RETURNS_DEEP_STUBS);
        when(database.getDataSource().getConnection()).thenThrow(new SQLException("down"));

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getDatabase).thenReturn(database);

            manager.reload();
        }

        assertSame(chat, manager.getQuest(1));
    }
}
