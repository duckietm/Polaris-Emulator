package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.commands.CommandHandler.CommandOutcome;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CommandHandlerOutcomeTest {
    private static final String FORBIDDEN_KEY = "outcometestforbidden";
    private static final String THROWING_KEY = "outcometestthrowing";

    static final class ForbiddenCommand extends Command {
        ForbiddenCommand() {
            super("cmd_outcome_test_forbidden", new String[] {FORBIDDEN_KEY});
        }

        @Override
        public boolean handle(GameClient gameClient, String[] params) {
            return true;
        }
    }

    static final class ThrowingCommand extends Command {
        ThrowingCommand() {
            super(null, new String[] {THROWING_KEY});
        }

        @Override
        public boolean handle(GameClient gameClient, String[] params) {
            throw new IllegalStateException("boom");
        }
    }

    @AfterEach
    @SuppressWarnings("unchecked")
    void unregister() throws Exception {
        Field field = CommandHandler.class.getDeclaredField("commands");
        field.setAccessible(true);
        Map<String, Command> commands = (Map<String, Command>) field.get(null);
        commands.remove(ForbiddenCommand.class.getName());
        commands.remove(ThrowingCommand.class.getName());
    }

    private static Habbo habbo() {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        Rank rank = mock(Rank.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        when(info.getRank()).thenReturn(rank);
        return habbo;
    }

    private static GameClient client(Habbo habbo) {
        GameClient client = mock(GameClient.class);
        when(client.getHabbo()).thenReturn(habbo);
        return client;
    }

    @Test
    void onlyFailuresAndMissingPermissionsAreKeptOutOfTheChat() {
        assertTrue(CommandOutcome.SUCCESS.consumesChat());
        assertTrue(CommandOutcome.FAILED.consumesChat());
        assertTrue(CommandOutcome.NO_PERMISSION.consumesChat());
        assertFalse(CommandOutcome.RETURNED_FALSE.consumesChat());
        assertFalse(CommandOutcome.UNKNOWN.consumesChat());
    }

    @Test
    void aForbiddenCommandStaysUnhandledForRconButIsAnsweredInChat() {
        CommandHandler.addCommand(new ForbiddenCommand());
        Habbo habbo = habbo();
        GameClient client = client(habbo);

        assertFalse(CommandHandler.handleCommand(client, ":" + FORBIDDEN_KEY));
        verify(habbo, never()).whisperLocalizedOrDefault(anyString(), anyString(), any());

        assertTrue(CommandHandler.handleChatCommand(client, ":" + FORBIDDEN_KEY));
        verify(habbo)
                .whisperLocalizedOrDefault(
                        eq(CommandHandler.NO_PERMISSION_KEY), anyString(), eq(RoomChatMessageBubbles.ALERT));
    }

    @Test
    void aThrowingCommandStaysUnhandledForRconButIsAnsweredInChat() {
        CommandHandler.addCommand(new ThrowingCommand());
        Habbo habbo = habbo();
        GameClient client = client(habbo);

        assertFalse(CommandHandler.handleCommand(client, ":" + THROWING_KEY));
        verify(habbo, never()).whisperLocalizedOrDefault(anyString(), anyString(), any());

        assertTrue(CommandHandler.handleChatCommand(client, ":" + THROWING_KEY));
        verify(habbo)
                .whisperLocalizedOrDefault(
                        eq(CommandHandler.GENERIC_ERROR_KEY), anyString(), eq(RoomChatMessageBubbles.ALERT));
    }

    @Test
    void unknownWordsStayChat() {
        Habbo habbo = habbo();
        GameClient client = client(habbo);

        assertFalse(CommandHandler.handleChatCommand(client, ":outcometestnosuchcommand"));
        assertFalse(CommandHandler.handleCommand(client, ":outcometestnosuchcommand"));
        verify(habbo, never()).whisperLocalizedOrDefault(anyString(), anyString(), any());
    }
}
