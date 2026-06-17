package com.eu.habbo.messages.incoming.modtool;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModToolTicketLifecycleContractTest {
    @Test
    void mutatingTicketActionsValidateOwnership() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool");

        for (String handler : List.of(
                "ModToolCloseTicketEvent.java",
                "ModToolIssueChangeTopicEvent.java",
                "ModToolReleaseTicketEvent.java"
        )) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(source.contains("ModToolTicketGuard.isOwnedBy(issue, this.client.getHabbo())"),
                    handler + " must only mutate tickets owned by the acting moderator");
        }
    }

    @Test
    void clientDrivenTicketAndChatlogIdsAreValidated() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool");

        for (String handler : List.of(
                "ModToolPickTicketEvent.java",
                "ModToolCloseTicketEvent.java",
                "ModToolIssueChangeTopicEvent.java",
                "ModToolRequestIssueChatlogEvent.java",
                "ModToolRequestRoomChatlogEvent.java",
                "ModToolRequestRoomUserChatlogEvent.java",
                "ModToolRequestUserChatlogEvent.java"
        )) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(source.contains("ModToolTicketGuard.isPositiveId"),
                    handler + " must reject zero or negative client-provided ids");
        }
    }

    @Test
    void releaseBatchAndCloseStateAreBounded() throws Exception {
        Path base = Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool");
        String release = Files.readString(base.resolve("ModToolReleaseTicketEvent.java"));
        String close = Files.readString(base.resolve("ModToolCloseTicketEvent.java"));

        assertTrue(release.contains("ModToolTicketGuard.isValidReleaseBatch(count)"),
                "release ticket batches must be bounded before reading ticket ids");
        assertTrue(close.contains("state < 1 || state > 3"),
                "close ticket must reject unknown close states before mutating the ticket");
    }

    @Test
    void changeTopicRequiresKnownCategory() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/modtool/ModToolIssueChangeTopicEvent.java"));

        assertTrue(source.contains("getCfhTopic(categoryId) == null"),
                "change-topic must reject unknown CFH categories before persisting");
    }
}
