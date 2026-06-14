package com.eu.habbo.messages.incoming.modtool;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModToolPermissionContractTest {
    private static final List<String> STAFF_ONLY_HANDLERS = List.of(
            "ModToolAlertEvent.java",
            "ModToolChangeRoomSettingsEvent.java",
            "ModToolCloseTicketEvent.java",
            "ModToolIssueChangeTopicEvent.java",
            "ModToolIssueDefaultSanctionEvent.java",
            "ModToolKickEvent.java",
            "ModToolPickTicketEvent.java",
            "ModToolReleaseTicketEvent.java",
            "ModToolRequestIssueChatlogEvent.java",
            "ModToolRequestRoomChatlogEvent.java",
            "ModToolRequestRoomInfoEvent.java",
            "ModToolRequestRoomUserChatlogEvent.java",
            "ModToolRequestRoomVisitsEvent.java",
            "ModToolRequestUserChatlogEvent.java",
            "ModToolRequestUserInfoEvent.java",
            "ModToolRoomAlertEvent.java",
            "ModToolSanctionAlertEvent.java",
            "ModToolSanctionBanEvent.java",
            "ModToolSanctionMuteEvent.java",
            "ModToolSanctionTradeLockEvent.java",
            "ModToolWarnEvent.java"
    );

    @Test
    void staffOnlyModToolHandlersRequireSupportToolPermission() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool");

        for (String handler : STAFF_ONLY_HANDLERS) {
            String source = Files.readString(base.resolve(handler));
            assertTrue(source.contains("hasPermission(Permission.ACC_SUPPORTTOOL)"),
                    handler + " must require ACC_SUPPORTTOOL before exposing moderator actions or private logs");
        }
    }

    @Test
    void modToolSanctionsCannotTargetSameOrHigherRanks() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool");

        for (String handler : List.of(
                "ModToolSanctionAlertEvent.java",
                "ModToolSanctionMuteEvent.java",
                "ModToolSanctionTradeLockEvent.java",
                "ModToolIssueDefaultSanctionEvent.java")) {
            String source = Files.readString(base.resolve(handler));
            assertTrue(source.contains("ModToolManager.canModerateTarget"),
                    handler + " must enforce the modtool target rank ceiling before applying sanctions");
        }

        String manager = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/modtool/ModToolManager.java"));
        assertTrue(manager.contains("!canModerateTarget(moderator, target.getHabboInfo().getId())"),
                "ModToolManager.alert must refuse alerts/warnings against same-or-higher-rank targets");
    }
}
