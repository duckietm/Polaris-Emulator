package polaris.messages.incoming.modtool;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModToolTicketLifecycleContractTest {
    @Test
    void mutatingTicketActionsValidateOwnership() throws Exception {
        Path base = Path.of("src/main/java/polaris/messages/incoming/modtool");

        for (String handler : List.of(
                "ModToolCloseTicketEvent.java",
                "ModToolIssueChangeTopicEvent.java",
                "ModToolReleaseTicketEvent.java")) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(
                    source.contains("ModToolTicketGuard.isOwnedBy(issue, this.client.getUser())"),
                    handler + " must only mutate tickets owned by the acting moderator");
        }
    }

    @Test
    void clientDrivenTicketAndChatlogIdsAreValidated() throws Exception {
        Path base = Path.of("src/main/java/polaris/messages/incoming/modtool");

        for (String handler : List.of(
                "ModToolPickTicketEvent.java",
                "ModToolCloseTicketEvent.java",
                "ModToolIssueChangeTopicEvent.java",
                "ModToolRequestIssueChatlogEvent.java",
                "ModToolRequestRoomChatlogEvent.java",
                "ModToolRequestRoomUserChatlogEvent.java",
                "ModToolRequestUserChatlogEvent.java")) {
            String source = Files.readString(base.resolve(handler));

            assertTrue(
                    source.contains("ModToolTicketGuard.isPositiveId"),
                    handler + " must reject zero or negative client-provided ids");
        }
    }

    @Test
    void releaseBatchAndCloseStateAreBounded() throws Exception {
        Path base = Path.of("src/main/java/polaris/messages/incoming/modtool");
        String release = Files.readString(base.resolve("ModToolReleaseTicketEvent.java"));
        String close = Files.readString(base.resolve("ModToolCloseTicketEvent.java"));

        assertTrue(
                release.contains("ModToolTicketGuard.isValidReleaseBatch(count)"),
                "release ticket batches must be bounded before reading ticket ids");
        assertTrue(
                close.contains("state < 1 || state > 3"),
                "close ticket must reject unknown close states before mutating the ticket");
    }

    @Test
    void pickAndCloseWalkTheWholeIdBatch() throws Exception {
        Path base = Path.of("src/main/java/polaris/messages/incoming/modtool");
        String pick = Files.readString(base.resolve("ModToolPickTicketEvent.java"));
        String close = Files.readString(base.resolve("ModToolCloseTicketEvent.java"));

        assertTrue(
                pick.contains("ModToolTicketGuard.isValidReleaseBatch(count)") && pick.contains("while (count-- > 0)"),
                "pick must read the id count and handle every id, not only the first");
        assertTrue(
                close.contains("ModToolTicketGuard.isValidReleaseBatch(count)")
                        && close.contains("while (count-- > 0)"),
                "close must read the id count and close every id, not only the first");
    }

    @Test
    void defaultSanctionCloseSkipsTheGroupedIdsBeforeTheTopic() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/modtool/ModToolIssueDefaultSanctionEvent.java"));

        assertTrue(
                source.contains("ModToolTicketGuard.isValidIdListSize(groupedCount)"),
                "the close with default action must bound the grouped id list it skips");
        assertTrue(
                source.contains("closeTicketAsHandled(issue, sender)"),
                "the close with default action must tell the reporter like every other close");
    }

    @Test
    void changeTopicRequiresKnownCategory() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/polaris/messages/incoming/modtool/ModToolIssueChangeTopicEvent.java"));

        assertTrue(
                source.contains("getCfhTopic(categoryId) == null"),
                "change-topic must reject unknown CFH categories before persisting");
    }
}
