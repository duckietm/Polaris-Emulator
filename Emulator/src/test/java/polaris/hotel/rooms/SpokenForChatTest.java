package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.ConfigurationManager;
import polaris.core.DatabaseLoggable;
import polaris.core.DatabaseLogger;
import polaris.hotel.GameEnvironment;
import polaris.hotel.modtool.WordFilter;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;

/**
 * :say and :shout sent their composer straight to the room: the line skipped the word filter and
 * never reached chatlogs_room, so a moderator could make another user say anything and the chat
 * log staff consult showed nothing.
 */
class SpokenForChatTest {

    private static User user(int id, String name) {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(id);
        UserStats stats = mock(UserStats.class);
        stats.chatColor = RoomChatMessageBubbles.NORMAL;
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.getUsername()).thenReturn(name);
        User user = mock(User.class);
        when(user.getRoomUnit()).thenReturn(unit);
        when(user.getUserStats()).thenReturn(stats);
        when(user.getUserInfo()).thenReturn(info);
        return user;
    }

    private static MockedStatic<Emulator> emulator(WordFilter filter, DatabaseLogger logger) {
        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString())).thenReturn(true);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getWordFilter()).thenReturn(filter);

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        emulator.when(Emulator::getDatabaseLogger).thenReturn(logger);
        return emulator;
    }

    @Test
    void theFilterJudgesTheAuthorAndNeverReportsTheSpeaker() {
        User moderator = user(1, "Mod");
        User target = user(2, "Target");
        WordFilter filter = mock(WordFilter.class);

        try (MockedStatic<Emulator> ignored = emulator(filter, mock(DatabaseLogger.class))) {
            RoomChatMessage chat = new RoomChatMessage("bad word", target, RoomChatMessageBubbles.NORMAL);
            chat.filterWrittenBy(moderator);

            verify(filter).filter(chat, moderator);
            verify(filter, never()).autoReportCheck(any());
            verify(target, never()).mute(anyInt(), anyBoolean());
        }
    }

    @Test
    void anAuthorWithoutFilterIsNotFiltered() {
        User moderator = user(1, "Mod");
        when(moderator.hasPermission(Permission.ACC_CHAT_NO_FILTER)).thenReturn(true);
        WordFilter filter = mock(WordFilter.class);

        try (MockedStatic<Emulator> ignored = emulator(filter, mock(DatabaseLogger.class))) {
            new RoomChatMessage("anything", user(2, "Target"), RoomChatMessageBubbles.NORMAL)
                    .filterWrittenBy(moderator);

            verify(filter, never()).filter(any(RoomChatMessage.class), any());
        }
    }

    @Test
    void theChatLogNamesWhoWroteTheLine() {
        AtomicReference<DatabaseLoggable> stored = new AtomicReference<>();
        DatabaseLogger logger = mock(DatabaseLogger.class);
        doAnswer(invocation -> {
                    stored.set(invocation.getArgument(0));
                    return null;
                })
                .when(logger)
                .store(any());
        boolean saved = RoomChatMessage.SAVE_ROOM_CHATS;
        RoomChatMessage.SAVE_ROOM_CHATS = true;

        try (MockedStatic<Emulator> ignored = emulator(mock(WordFilter.class), logger)) {
            new RoomChatMessage("hello", user(2, "Target"), RoomChatMessageBubbles.NORMAL)
                    .logWrittenBy(user(1, "Mod"), "say");
        } finally {
            RoomChatMessage.SAVE_ROOM_CHATS = saved;
        }

        RoomChatMessage logged = (RoomChatMessage) stored.get();
        assertEquals("hello [:say Mod]", logged.getUnfilteredMessage());
        assertEquals(2, logged.getUser().getUserInfo().getId(), "logged under the user made to speak");
    }

    @Test
    void bothCommandsFilterAndLog() throws Exception {
        for (String command : new String[] {"SayCommand.java", "ShoutCommand.java"}) {
            String source = Files.readString(Path.of("src/main/java/polaris/hotel/commands", command));

            assertTrue(source.contains("chat.filterWrittenBy(gameClient.getUser());"), command);
            assertTrue(source.contains("chat.logWrittenBy(gameClient.getUser(), "), command);
        }
    }
}
