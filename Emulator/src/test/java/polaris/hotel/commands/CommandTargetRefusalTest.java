package polaris.hotel.commands;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.permissions.PermissionsManager;
import polaris.hotel.permissions.Rank;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/**
 * Commands that act on another user went around the central rank guard: a lower rank could empty
 * an admin's inventory or take their badges (offline too), and the emptied user was sent the
 * moderator's inventory. :control, :freeze, :summon, :say, :shout, :allow_trading and the
 * bot and pet :empty commands let a lower rank drive, freeze, move, speak for or trade-lock an
 * admin. Every such command now asks {@link CommandTargetGuard#refuse}.
 */
class CommandTargetRefusalTest {

    private static final Path COMMANDS = Path.of("src/main/java/polaris/hotel/commands");

    private static final List<String> GUARDED = List.of(
            "AllowTradingCommand.java",
            "ControlCommand.java",
            "EmptyBotsInventoryCommand.java",
            "EmptyInventoryCommand.java",
            "EmptyPetsInventoryCommand.java",
            "FreezeCommand.java",
            "SayCommand.java",
            "ShoutCommand.java",
            "SummonCommand.java",
            "TakeBadgeCommand.java");

    private static User user(int id, int rankId) {
        Rank rank = mock(Rank.class);
        when(rank.getId()).thenReturn(rankId);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(id);
        when(info.getRank()).thenReturn(rank);
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(info);
        return user;
    }

    private static boolean refuse(User issuer, User target) {
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
        User moderator = user(1, 4);

        assertTrue(refuse(moderator, user(2, 6)));
        assertTrue(refuse(moderator, user(3, 4)));
        verify(moderator, times(2)).whisperLocalized(CommandTargetGuard.REFUSED_KEY, RoomChatMessageBubbles.ALERT);
    }

    @Test
    void aLowerRankOneselfOrNobodyIsLeftToTheCommand() {
        User moderator = user(1, 4);

        assertFalse(refuse(moderator, user(2, 2)), "a lower rank may be acted on");
        assertFalse(refuse(moderator, moderator), "acting on oneself is the command's business");
        assertFalse(refuse(moderator, null), "a missing target keeps the command's own not-found answer");
        verify(moderator, never()).whisperLocalized(anyString(), any(RoomChatMessageBubbles.class));
    }

    @Test
    void theTopRankMayActOnItsPeers() {
        assertFalse(refuse(user(1, 7), user(2, 7)));
    }

    @Test
    void everyCommandOnAnotherUserAsksTheGuard() throws Exception {
        for (String command : GUARDED) {
            String source = Files.readString(COMMANDS.resolve(command));

            assertTrue(
                    source.contains("CommandTargetGuard.refuse(gameClient.getUser(), "),
                    command + " has to ask the guard");
        }
    }

    @Test
    void anEmptiedUserIsSentTheirOwnInventory() throws Exception {
        String source =
                Files.readString(COMMANDS.resolve("EmptyInventoryCommand.java")).replaceAll("\\s+", "");

        assertTrue(
                source.contains("newInventoryItemsComposer(0,1,user.getInventory().getItemsComponent().getItems())"));
        assertFalse(source.contains("gameClient.getUser().getInventory()"), "not the moderator's inventory");
    }
}
