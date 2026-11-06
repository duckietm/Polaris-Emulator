package polaris.hotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.users.User;
import polaris.hotel.users.UserManager;

/**
 * Currency commands went through giveCredits/givePoints, which record the receiving user as the actor with a generic
 * reason - a :masscredits read as a thousand self-credits in the economy audit log - and the commands reported success
 * even when the ledger refused.
 */
class CommandEconomyTest {

    @Test
    void aGrantIsRecordedAsTheStaffMembersWithTheCommandAsReason() {
        User target = mock(User.class);
        when(target.applyCredits(anyInt(), anyString(), anyString(), eq(42))).thenReturn(true);

        assertTrue(CommandEconomy.credits(target, 500, "cmd_masscredits", 42));
        verify(target).applyCredits(eq(500), eq("command.cmd_masscredits"), startsWith("cmd_masscredits:42:"), eq(42));
    }

    @Test
    void aRefusedGrantIsReportedAsRefused() {
        User target = mock(User.class);
        when(target.applyPoints(anyInt(), anyInt(), anyString(), anyString(), eq(42)))
                .thenReturn(false);

        assertFalse(CommandEconomy.points(target, 5, -999_999, "cmd_points", 42));
        verify(target).applyPoints(eq(5), eq(-999_999), eq("command.cmd_points"), startsWith("cmd_points:42:"), eq(42));
    }

    @Test
    void anOfflineUserIsGrantedThroughTheManagerWithTheSameActor() {
        UserManager users = mock(UserManager.class);
        when(users.applyCredits(anyInt(), anyInt(), anyString(), anyString(), eq(42)))
                .thenReturn(true);

        assertTrue(CommandEconomy.credits(users, 7, 100, "cmd_credits", 42));
        verify(users).applyCredits(eq(7), eq(100), eq("command.cmd_credits"), startsWith("cmd_credits:42:"), eq(42));
    }

    @Test
    void everyCurrencyCommandGoesThroughIt() throws Exception {
        for (String command : List.of(
                "CreditsCommand",
                "PixelCommand",
                "PointsCommand",
                "MassCreditsCommand",
                "MassPixelsCommand",
                "MassPointsCommand",
                "RoomCreditsCommand",
                "RoomPixelsCommand",
                "RoomPointsCommand")) {
            String source = Files.readString(Path.of("src/main/java/polaris/hotel/commands/" + command + ".java"));

            assertTrue(source.contains("CommandEconomy."), command);
            assertFalse(
                    source.matches("(?s).*\\.give(Credits|Pixels|Points)\\(.*"),
                    command + " still grants without an outcome");
        }
    }
}
