package com.eu.habbo.messages.incoming.modtool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The forum comment, thread, photo and private chat reports filed a ticket and alerted every moderator for each
 * packet, from muted users and users with a ticket already open included.
 */
class ModToolReportGateTest {

    private static GameClient client(boolean mayTalk) {
        GameClient client = mock(GameClient.class, RETURNS_DEEP_STUBS);
        when(client.getHabbo().getHabboStats().allowTalk()).thenReturn(mayTalk);
        return client;
    }

    @Test
    void aMutedUserFilesNoReport() {
        GameClient client = client(false);
        ModToolManager modTools = mock(ModToolManager.class);

        assertTrue(ModToolReportGate.refuse(client, modTools));
        verify(modTools, never()).openTicketsForHabbo(any());
    }

    @Test
    void aUserWithAnOpenTicketIsShownItInstead() {
        GameClient client = client(true);
        ModToolManager modTools = mock(ModToolManager.class);
        when(modTools.openTicketsForHabbo(any())).thenReturn(List.of(mock(ModToolIssue.class)));

        assertTrue(ModToolReportGate.refuse(client, modTools));
    }

    @Test
    void anyOtherReportGoesThrough() {
        GameClient client = client(true);
        ModToolManager modTools = mock(ModToolManager.class);
        when(modTools.openTicketsForHabbo(any())).thenReturn(List.of());

        assertFalse(ModToolReportGate.refuse(client, modTools));
    }

    @Test
    void theFourReportPacketsUseTheGateAndARateLimit() throws Exception {
        for (String handler : new String[] {
            "ReportCommentEvent", "ReportThreadEvent", "ReportPhotoEvent", "ReportFriendPrivateChatEvent"
        }) {
            String source = Files.readString(
                    Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool/" + handler + ".java"));

            assertTrue(source.contains("if (ModToolReportGate.refuse(this.client, modTools)) return;"), handler);
            assertTrue(source.contains("return ModToolReportGate.RATE_LIMIT_MS;"), handler);
        }
    }
}
