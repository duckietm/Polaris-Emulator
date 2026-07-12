package com.eu.habbo.messages.incoming.modtool;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModToolReportInputContractTest {
    @Test
    void reportHandlersNormalizeAndBoundFreeText() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool");

        for (String handler : List.of(
                "ReportEvent.java",
                "ReportFriendPrivateChatEvent.java",
                "ReportCommentEvent.java",
                "ReportThreadEvent.java"
        )) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(source.contains("ModToolReportInputGuard.normalize"),
                    handler + " must normalize report text before persistence or staff broadcast");
            assertTrue(source.contains("ModToolReportInputGuard.isValidReportMessage"),
                    handler + " must reject empty or oversized report text");
        }
    }

    @Test
    void reportHandlersRejectInvalidIdsAndCounts() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool");

        for (String handler : List.of(
                "ReportEvent.java",
                "ReportFriendPrivateChatEvent.java",
                "ReportCommentEvent.java",
                "ReportThreadEvent.java",
                "ReportBullyEvent.java",
                "ReportPhotoEvent.java"
        )) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(source.contains("ModToolReportInputGuard.isPositiveId"),
                    handler + " must reject zero or negative ids supplied by the client");
        }

        String privateChat = Files.readString(base.resolve("ReportFriendPrivateChatEvent.java"));
        assertTrue(privateChat.contains("ModToolReportInputGuard.isValidPrivateChatLogCount(count)"),
                "private chat reports must reject negative or oversized client-provided chatlog counts");
    }

    @Test
    void reportEventValidatesTopicBeforeUsingReply() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool/ReportEvent.java"));

        assertTrue(source.indexOf("if (cfhTopic == null)") < source.indexOf("cfhTopic.reply"),
                "ReportEvent must reject unknown topics before dereferencing the reply text");
    }

    @Test
    void bullyReportUsesSameMutedUserGateAsNormalReports() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool/ReportBullyEvent.java"));

        assertTrue(source.contains("if (!this.client.getHabbo().getHabboStats().allowTalk())"),
                "bully reports must reject muted users instead of rejecting users who can talk");
    }

    @Test
    void privateChatReportBindsEveryLogAuthorToTheConversation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool/ReportFriendPrivateChatEvent.java"));

        int authorRead = source.indexOf("int chatUserId = this.packet.readInt()");
        int participantGuard = source.indexOf("isPrivateChatParticipant(chatUserId", authorRead);
        int messageRead = source.indexOf("this.packet.readString()", authorRead);

        assertTrue(authorRead > -1, "private chat reports must read the composer author id");
        assertTrue(participantGuard > authorRead, "private chat reports must bind authors to reporter or reported user");
        assertTrue(participantGuard < messageRead, "invalid authors must be rejected before their message is accepted");
        assertTrue(!source.contains("this.packet.readInt() == info.getId()"),
                "private chat reports must not consume a second, unauthenticated author id");
    }
}
