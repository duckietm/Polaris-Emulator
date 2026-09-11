package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.forums.ForumThread;
import com.eu.habbo.habbohotel.modtool.CfhTopic;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModToolReportReceivedAlertComposer;
import com.eu.habbo.threading.runnables.InsertModToolIssue;

public class ReportCommentEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int groupId = this.packet.readInt();
        int threadId = this.packet.readInt();
        int commentId = this.packet.readInt();
        int topicId = this.packet.readInt();
        String message = ModToolReportInputGuard.normalize(this.packet.readString());
        // Official class_2390 and friends end with the unlawful-activity reporter name and
        // e-mail; older clients stop before them, so they are read only while bytes remain.
        String reporterName = "";
        String reporterEmail = "";

        if (this.packet.bytesAvailable() > 0) {
            reporterName = this.packet.readString();
        }

        if (this.packet.bytesAvailable() > 0) {
            reporterEmail = this.packet.readString();
        }

        if (!ModToolReportInputGuard.isPositiveId(groupId)
                || !ModToolReportInputGuard.isPositiveId(threadId)
                || !ModToolReportInputGuard.isPositiveId(commentId)
                || !ModToolReportInputGuard.isPositiveId(topicId)
                || !ModToolReportInputGuard.isValidReportMessage(message)) {
            return;
        }

        message = ModToolReportInputGuard.withReporterContact(message, reporterName, reporterEmail);

        CfhTopic topic = Emulator.getGameEnvironment().getModToolManager().getCfhTopic(topicId);

        if (topic == null) return;

        ForumThread thread = ForumThread.getById(threadId);

        if (thread == null) return;

        Habbo opener = Emulator.getGameEnvironment().getHabboManager().getHabbo(thread.getOpenerId());

        ModToolIssue issue = new ModToolIssue(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                thread.getOpenerId(),
                opener == null ? "" : opener.getHabboInfo().getUsername(),
                0,
                message,
                ModToolTicketType.DISCUSSION);
        issue.category = topicId;
        issue.groupId = groupId;
        issue.threadId = threadId;
        issue.commentId = commentId;
        new InsertModToolIssue(issue).run();

        this.client.sendResponse(
                new ModToolReportReceivedAlertComposer(ModToolReportReceivedAlertComposer.REPORT_RECEIVED, message));
        Emulator.getGameEnvironment().getModToolManager().addTicket(issue);
        Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);
    }
}
