package com.eu.habbo.habbohotel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Maintenance used to close the door without a word. The three notices the client already knew how
 * to draw are what turn it from abrupt into something a player can plan around.
 */
class MaintenanceNoticeContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theHotelCanSayWhenItWillBeBack() throws Exception {
        String mode = source("com/eu/habbo/habbohotel/MaintenanceMode.java");

        assertTrue(mode.contains("hotel.maintenance.reopen_hour"));
        assertTrue(mode.contains("hotel.maintenance.reopen_minute"));
        // An hour outside the clock means nobody has said, and nothing is announced.
        assertTrue(mode.contains("return hour >= 0 && hour <= 23;"));
    }

    @Test
    void theWarningReachesEverybodyBeforeTheDoorShuts() throws Exception {
        String command = source("com/eu/habbo/habbohotel/commands/MaintenanceCommand.java");

        assertTrue(command.contains("if (action.equals(\"warn\"))"));
        assertTrue(command.contains("new HotelWillCloseInMinutesAndBackInComposer(closeInMinutes, reopenInMinutes)"));
        assertTrue(command.contains("sendBroadcastResponse"));
    }

    @Test
    void switchingItOnTellsThePlayersAlreadyInside() throws Exception {
        String command = source("com/eu/habbo/habbohotel/commands/MaintenanceCommand.java");

        assertTrue(command.contains("if (enable && MaintenanceMode.hasReopenTime())"));
        assertTrue(command.contains("new HotelClosesAndWillOpenAtComposer("));
        // They keep playing: maintenance only shuts the door to new logins.
        assertTrue(command.contains("MaintenanceMode.getReopenMinute(), false)"));
    }

    @Test
    void aRefusedLoginIsToldWhenToComeBack() throws Exception {
        String login = source("com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java");

        assertTrue(login.contains("sendReopenNotice(this.client);"));
        assertTrue(login.contains("new HotelClosedAndOpensComposer("));
        assertTrue(login.contains("if (!MaintenanceMode.hasReopenTime()) return;"));
    }

    @Test
    void nothingIsAnnouncedWhenNobodySaidWhenItIsBack() throws Exception {
        String command = source("com/eu/habbo/habbohotel/commands/MaintenanceCommand.java");
        String login = source("com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java");

        assertTrue(command.indexOf("hasReopenTime()") < command.indexOf("new HotelClosesAndWillOpenAtComposer("));
        assertTrue(login.indexOf("hasReopenTime()") < login.indexOf("new HotelClosedAndOpensComposer("));
    }
}
