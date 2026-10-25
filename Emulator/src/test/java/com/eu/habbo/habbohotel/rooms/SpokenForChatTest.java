package com.eu.habbo.habbohotel.rooms;

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

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.DatabaseLoggable;
import com.eu.habbo.core.DatabaseLogger;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * :say and :shout sent their composer straight to the room: the line skipped the word filter and
 * never reached chatlogs_room, so a moderator could make another user say anything and the chat
 * log staff consult showed nothing.
 */
class SpokenForChatTest {

    private static Habbo habbo(int id, String name) {
        RoomUnit unit = mock(RoomUnit.class);
        when(unit.getId()).thenReturn(id);
        HabboStats stats = mock(HabboStats.class);
        stats.chatColor = RoomChatMessageBubbles.NORMAL;
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.getUsername()).thenReturn(name);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getRoomUnit()).thenReturn(unit);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
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
        Habbo moderator = habbo(1, "Mod");
        Habbo target = habbo(2, "Target");
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
        Habbo moderator = habbo(1, "Mod");
        when(moderator.hasPermission(Permission.ACC_CHAT_NO_FILTER)).thenReturn(true);
        WordFilter filter = mock(WordFilter.class);

        try (MockedStatic<Emulator> ignored = emulator(filter, mock(DatabaseLogger.class))) {
            new RoomChatMessage("anything", habbo(2, "Target"), RoomChatMessageBubbles.NORMAL)
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
            new RoomChatMessage("hello", habbo(2, "Target"), RoomChatMessageBubbles.NORMAL)
                    .logWrittenBy(habbo(1, "Mod"), "say");
        } finally {
            RoomChatMessage.SAVE_ROOM_CHATS = saved;
        }

        RoomChatMessage logged = (RoomChatMessage) stored.get();
        assertEquals("hello [:say Mod]", logged.getUnfilteredMessage());
        assertEquals(2, logged.getHabbo().getHabboInfo().getId(), "logged under the user made to speak");
    }

    @Test
    void bothCommandsFilterAndLog() throws Exception {
        for (String command : new String[] {"SayCommand.java", "ShoutCommand.java"}) {
            String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands", command));

            assertTrue(source.contains("chat.filterWrittenBy(gameClient.getHabbo());"), command);
            assertTrue(source.contains("chat.logWrittenBy(gameClient.getHabbo(), "), command);
        }
    }
}
