package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.permissions.PermissionsManager;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Commands that act on another user went around the central rank guard: a lower rank could empty
 * an admin's inventory or take their badges (offline too), and the emptied user was sent the
 * moderator's inventory. Every such command now asks {@link CommandTargetGuard#refuse}.
 */
class CommandTargetRefusalTest {

    private static final Path COMMANDS = Path.of("src/main/java/com/eu/habbo/habbohotel/commands");

    private static final List<String> GUARDED = List.of("EmptyInventoryCommand.java", "TakeBadgeCommand.java");

    private static Habbo habbo(int id, int rankId) {
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(rankId);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.getRank()).thenReturn(rank);
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    private static boolean refuse(Habbo issuer, Habbo target) {
        Rank top = mock(Rank.class);
        when(top.getId()).thenReturn(7);
        PermissionsManager permissions = mock(PermissionsManager.class);
        when(permissions.getAllRanks()).thenReturn(List.of(top));
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getPermissionsManager()).thenReturn(permissions);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            return CommandTargetGuard.refuse(issuer, target);
        }
    }

    @Test
    void aHigherOrEqualRankIsRefusedWithAWhisper() {
        Habbo moderator = habbo(1, 4);

        assertTrue(refuse(moderator, habbo(2, 6)));
        assertTrue(refuse(moderator, habbo(3, 4)));
        verify(moderator, times(2)).whisperLocalized(CommandTargetGuard.REFUSED_KEY, RoomChatMessageBubbles.ALERT);
    }

    @Test
    void aLowerRankOneselfOrNobodyIsLeftToTheCommand() {
        Habbo moderator = habbo(1, 4);

        assertFalse(refuse(moderator, habbo(2, 2)), "a lower rank may be acted on");
        assertFalse(refuse(moderator, moderator), "acting on oneself is the command's business");
        assertFalse(refuse(moderator, null), "a missing target keeps the command's own not-found answer");
        verify(moderator, never()).whisperLocalized(anyString(), any(RoomChatMessageBubbles.class));
    }

    @Test
    void theTopRankMayActOnItsPeers() {
        assertFalse(refuse(habbo(1, 7), habbo(2, 7)));
    }

    @Test
    void everyCommandOnAnotherUserAsksTheGuard() throws Exception {
        for (String command : GUARDED) {
            String source = Files.readString(COMMANDS.resolve(command));

            assertTrue(
                    source.contains("CommandTargetGuard.refuse(gameClient.getHabbo(), "),
                    command + " has to ask the guard");
        }
    }

    @Test
    void anEmptiedUserIsSentTheirOwnInventory() throws Exception {
        String source =
                Files.readString(COMMANDS.resolve("EmptyInventoryCommand.java")).replaceAll("\\s+", "");

        assertTrue(
                source.contains("newInventoryItemsComposer(0,1,habbo.getInventory().getItemsComponent().getItems())"));
        assertFalse(source.contains("gameClient.getHabbo().getInventory()"), "not the moderator's inventory");
    }
}
