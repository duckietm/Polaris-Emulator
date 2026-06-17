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
    void modToolSanctionsCannotTargetPeerRanksUnlessOperatorIsCoreRank() throws Exception {
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
                "ModToolManager.alert must refuse alerts/warnings against protected targets");
        assertTrue(manager.contains("targetRankId < moderatorRankId"),
                "non-core moderators must only target lower-ranked users");
        assertTrue(manager.contains("isCoreRank(moderatorRankId) && targetRankId <= moderatorRankId"),
                "highest/core moderators should be allowed to target peer ranks");
        assertTrue(manager.contains("private static boolean isCoreRank(int rankId)"),
                "core-rank detection should be centralized in ModToolManager");
    }

    @Test
    void managerEntryPointsShareTargetAndRoomOwnerGuards() throws Exception {
        String manager = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/modtool/ModToolManager.java"));
        String sanctions = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/modtool/ModToolSanctions.java"));
        String defaultSanction = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool/ModToolIssueDefaultSanctionEvent.java"));

        assertTrue(manager.contains("!canModerateTarget(moderator, targetUserId)"),
                "ModToolManager.ban must use the central target-rank guard for offline and online users");
        assertTrue(manager.contains("!canModerateTarget(moderator, h.getHabboInfo().getId())"),
                "IP and machine fan-out bans must skip protected peer-or-higher ranked sessions");
        assertTrue(manager.contains("!canModerateTarget(moderator, room.getOwnerId())"),
                "ModToolManager.roomAction must refuse mutations on rooms owned by protected ranks");
        assertTrue(sanctions.contains("!ModToolManager.canModerateTarget(self, habboId)"),
                "ModToolSanctions.run must guard every sanction path before writing or applying it");
        assertTrue(defaultSanction.contains("if (issue == null)"),
                "default sanctions must tolerate stale or missing ticket ids");
    }

    @Test
    void staffSuppliedModToolMessagesAreBounded() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool");

        for (String handler : List.of(
                "ModToolAlertEvent.java",
                "ModToolWarnEvent.java",
                "ModToolKickEvent.java",
                "ModToolRoomAlertEvent.java",
                "ModToolSanctionAlertEvent.java",
                "ModToolSanctionBanEvent.java",
                "ModToolSanctionMuteEvent.java",
                "ModToolSanctionTradeLockEvent.java"
        )) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(source.contains("ModToolInputGuard.normalize"),
                    handler + " must normalize staff-supplied text before use");
            assertTrue(source.contains("ModToolInputGuard.isSafeMessage"),
                    handler + " must reject empty or oversized staff-supplied text");
        }
    }

    @Test
    void staffSuppliedModToolTargetsArePositiveBeforeLookupOrMutation() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool");

        for (String handler : List.of(
                "ModToolAlertEvent.java",
                "ModToolWarnEvent.java",
                "ModToolKickEvent.java",
                "ModToolChangeRoomSettingsEvent.java",
                "ModToolRequestRoomInfoEvent.java",
                "ModToolRequestRoomVisitsEvent.java",
                "ModToolIssueDefaultSanctionEvent.java",
                "ModToolSanctionAlertEvent.java",
                "ModToolSanctionBanEvent.java",
                "ModToolSanctionMuteEvent.java",
                "ModToolSanctionTradeLockEvent.java"
        )) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(source.contains("ModToolTicketGuard.isPositiveId"),
                    handler + " must reject zero or negative client-provided ids before manager/database lookups");
        }
    }
}
