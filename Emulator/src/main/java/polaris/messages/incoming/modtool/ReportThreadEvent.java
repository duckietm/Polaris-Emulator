package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.guilds.forums.ForumThread;
import polaris.hotel.modtool.CfhTopic;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.modtool.ModToolTicketType;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModToolReportReceivedAlertComposer;
import polaris.threading.runnables.InsertModToolIssue;

public class ReportThreadEvent extends MessageHandler {
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
                || !ModToolReportInputGuard.isPositiveId(topicId)
                || !ModToolReportInputGuard.isValidReportMessage(message)) {
            return;
        }

        message = ModToolReportInputGuard.withReporterContact(message, reporterName, reporterEmail);

        CfhTopic topic = modTools.getCfhTopic(topicId);

        if (topic == null) return;

        ForumThread thread = ForumThread.getById(threadId);

        if (thread == null) return;

        User opener = Emulator.getGameEnvironment().getUserManager().getUser(thread.getOpenerId());

        ModToolIssue issue = new ModToolIssue(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                thread.getOpenerId(),
                opener == null ? "" : opener.getUserInfo().getUsername(),
                0,
                message,
                ModToolTicketType.DISCUSSION);
        issue.category = topicId;
        issue.groupId = groupId;
        issue.threadId = threadId;
        new InsertModToolIssue(issue).run();

        this.client.sendResponse(
                new ModToolReportReceivedAlertComposer(ModToolReportReceivedAlertComposer.REPORT_RECEIVED, message));
        modTools.addTicket(issue);
        modTools.updateTicketToMods(issue);
    }
}
