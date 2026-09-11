package com.eu.habbo.messages.incoming.modtool;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeletePendingCallsForHelpEventTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theHeaderMatchesTheRendererAndIsRegistered() throws Exception {
        assertTrue(source("com/eu/habbo/messages/incoming/Incoming.java")
                .contains("DeletePendingCallsForHelpEvent = 3605"));
        assertTrue(source("com/eu/habbo/messages/PacketManager.java")
                .contains("Incoming.DeletePendingCallsForHelpEvent, DeletePendingCallsForHelpEvent.class"));
    }

    @Test
    void everyOpenCallIsWithdrawnAndTheClientIsTold() throws Exception {
        String handler = source("com/eu/habbo/messages/incoming/modtool/DeletePendingCallsForHelpEvent.java");

        assertTrue(handler.contains("openTicketsForHabbo(habbo)"));
        assertTrue(handler.contains("closeTicketAsWithdrawn(issue)"));
        assertTrue(handler.contains("new UnknownHelperComposer()"));
    }

    @Test
    void withdrawingSendsNoVerdictToTheReporter() throws Exception {
        String manager = source("com/eu/habbo/habbohotel/modtool/ModToolManager.java");
        int start = manager.indexOf("public void closeTicketAsWithdrawn(ModToolIssue issue) {");
        int end = manager.indexOf("public void closeTicketAsUseless(");

        assertTrue(start > 0 && end > start);

        String body = manager.substring(start, end);

        assertTrue(body.contains("issue.state = ModToolTicketState.CLOSED;"));
        assertTrue(body.contains("issue.sanctioned = false;"));
        assertTrue(body.contains("this.removeTicket(issue);"));
        assertTrue(!body.contains("ModToolIssueHandledComposer"));
    }
}
