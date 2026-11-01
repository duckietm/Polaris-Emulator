package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guilds.forums.ForumThread;
import com.eu.habbo.habbohotel.guilds.forums.ForumThreadComment;
import com.eu.habbo.habbohotel.modtool.CfhTopic;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolManager;
import com.eu.habbo.habbohotel.modtool.ModToolTicketType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModToolReportReceivedAlertComposer;
import com.eu.habbo.threading.runnables.InsertModToolIssue;

public class ReportCommentEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return ModToolReportGate.RATE_LIMIT_MS;
    }

    @Override
    public void handle() throws Exception {
        ModToolManager modTools = Emulator.getGameEnvironment().getModToolManager();
        if (ModToolReportGate.refuse(this.client, modTools)) return;

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

        CfhTopic topic = modTools.getCfhTopic(topicId);

        if (topic == null) return;

        ForumThread thread = ForumThread.getById(threadId);
        ForumThreadComment comment = ForumThreadComment.getById(commentId);

        // The comment's author is the one reported, and only for a comment of that thread in that group:
        // the ticket used to name whoever opened the thread.
        if (thread == null || thread.getGuildId() != groupId || comment == null || comment.getThreadId() != threadId)
            return;

        Habbo author = Emulator.getGameEnvironment().getHabboManager().getHabbo(comment.getUserId());

        ModToolIssue issue = new ModToolIssue(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                comment.getUserId(),
                author == null ? "" : author.getHabboInfo().getUsername(),
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
        modTools.addTicket(issue);
        modTools.updateTicketToMods(issue);
    }
}
