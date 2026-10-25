package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Currency commands went through giveCredits/givePoints, which record the receiving user as the actor with a generic
 * reason - a :masscredits read as a thousand self-credits in the economy audit log - and the commands reported success
 * even when the ledger refused.
 */
class CommandEconomyTest {

    @Test
    void aGrantIsRecordedAsTheStaffMembersWithTheCommandAsReason() {
        Habbo target = mock(Habbo.class);
        when(target.applyCredits(anyInt(), anyString(), anyString(), eq(42))).thenReturn(true);

        assertTrue(CommandEconomy.credits(target, 500, "cmd_masscredits", 42));
        verify(target).applyCredits(eq(500), eq("command.cmd_masscredits"), startsWith("cmd_masscredits:42:"), eq(42));
    }

    @Test
    void aRefusedGrantIsReportedAsRefused() {
        Habbo target = mock(Habbo.class);
        when(target.applyPoints(anyInt(), anyInt(), anyString(), anyString(), eq(42)))
                .thenReturn(false);

        assertFalse(CommandEconomy.points(target, 5, -999_999, "cmd_points", 42));
        verify(target).applyPoints(eq(5), eq(-999_999), eq("command.cmd_points"), startsWith("cmd_points:42:"), eq(42));
    }

    @Test
    void anOfflineUserIsGrantedThroughTheManagerWithTheSameActor() {
        HabboManager habbos = mock(HabboManager.class);
        when(habbos.applyCredits(anyInt(), anyInt(), anyString(), anyString(), eq(42)))
                .thenReturn(true);

        assertTrue(CommandEconomy.credits(habbos, 7, 100, "cmd_credits", 42));
        verify(habbos).applyCredits(eq(7), eq(100), eq("command.cmd_credits"), startsWith("cmd_credits:42:"), eq(42));
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
            String source =
                    Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands/" + command + ".java"));

            assertTrue(source.contains("CommandEconomy."), command);
            assertFalse(
                    source.matches("(?s).*\\.give(Credits|Pixels|Points)\\(.*"),
                    command + " still grants without an outcome");
        }
    }
}
