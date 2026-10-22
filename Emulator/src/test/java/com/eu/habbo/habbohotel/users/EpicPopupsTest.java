package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.messages.ServerMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AIR {@code HabboEpicPopupView}: the image URI of {@code EpicPopupMessageEvent} goes to a 200x200 bitmap. */
class EpicPopupsTest {
    @Test
    void aPlainNameIsAnImageOfTheLibraryFolder() {
        assertEquals("${image.library.url}notifications/summer.png", EpicPopups.assetUri("summer", "notifications/"));
        assertEquals(
                "${image.library.url}notifications/summer.gif", EpicPopups.assetUri("summer.gif", "notifications"));
        assertEquals("${image.library.url}album1584/X.png", EpicPopups.assetUri("album1584/X", ""));
    }

    @Test
    void urlsAndAssetUrisPassThrough() {
        assertEquals("https://example.org/a.png", EpicPopups.assetUri(" https://example.org/a.png ", "x/"));
        assertEquals(
                "${image.library.url}web_promo/a.png",
                EpicPopups.assetUri("${image.library.url}web_promo/a.png", "x/"));
    }

    @Test
    void anythingElseIsRefused() {
        assertNull(EpicPopups.assetUri(null, ""));
        assertNull(EpicPopups.assetUri("  ", ""));
        assertNull(EpicPopups.assetUri("../secret", ""));
        assertNull(EpicPopups.assetUri("javascript:alert(1)", ""));
        assertNull(EpicPopups.assetUri("a b", ""));
        assertNull(EpicPopups.assetUri("x".repeat(EpicPopups.MAX_LENGTH + 1), ""));
    }

    @Test
    void usersWithStaffAlertsOffAreSkipped() {
        Habbo shown = mock(Habbo.class);
        GameClient shownClient = mock(GameClient.class);
        when(shown.getClient()).thenReturn(shownClient);
        when(shown.getHabboStats()).thenReturn(mock(HabboStats.class));

        Habbo muted = mock(Habbo.class);
        GameClient mutedClient = mock(GameClient.class);
        HabboStats stats = mock(HabboStats.class);
        stats.blockStaffAlerts = true;
        when(muted.getClient()).thenReturn(mutedClient);
        when(muted.getHabboStats()).thenReturn(stats);

        assertEquals(1, EpicPopups.show(List.of(shown, muted), "${image.library.url}notifications/a.png"));
        verify(shownClient).sendResponse(any(ServerMessage.class));
        verify(mutedClient, never()).sendResponse(any(ServerMessage.class));
        assertEquals(0, EpicPopups.show(List.of(shown), null));
    }

    @Test
    void theCommandAndTheRconCommandAreRegistered() throws Exception {
        String commands =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/commands/CommandHandler.java"));
        String rcon = Files.readString(Path.of("src/main/java/com/eu/habbo/networking/rconserver/RCONServer.java"));

        assertTrue(commands.contains("addCommand(new EpicPopupCommand());"));
        assertTrue(rcon.contains("addRCONMessage(\"epicpopup\", EpicPopup.class);"));
    }
}
