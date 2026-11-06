package polaris.messages.incoming.modtool;

import java.util.ArrayList;
import polaris.Emulator;
import polaris.hotel.modtool.ModToolChatLog;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.modtool.ModToolTicketType;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.HelperRequestDisabledComposer;
import polaris.threading.runnables.InsertModToolIssue;

public class ReportFriendPrivateChatEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return ModToolReportGate.RATE_LIMIT_MS;
    }

    @Override
    public void handle() throws Exception {
        ModToolManager modTools = Emulator.getGameEnvironment().getModToolManager();
        if (ModToolReportGate.refuse(this.client, modTools)) return;

        if (!this.client.getUser().getUserStats().allowTalk()) {
            this.client.sendResponse(new HelperRequestDisabledComposer());
            return;
        }

        String message = ModToolReportInputGuard.normalize(this.packet.readString());
        int category = this.packet.readInt();
        int userId = this.packet.readInt();
        int count = this.packet.readInt();
        ArrayList<ModToolChatLog> chatLogs = new ArrayList<>();

        if (!ModToolReportInputGuard.isValidReportMessage(message)
                || category <= 0
                || !ModToolReportInputGuard.isPositiveId(userId)
                || userId == this.client.getUser().getUserInfo().getId()
                || !ModToolReportInputGuard.isValidPrivateChatLogCount(count)) {
            return;
        }

        UserInfo info;
        User target = Emulator.getGameEnvironment().getUserManager().getUser(userId);
        if (target != null) {
            info = target.getUserInfo();
        } else {
            info = UserManager.getOfflineUserInfo(userId);
        }

        if (info == null) return;

        int reporterId = this.client.getUser().getUserInfo().getId();
        for (int i = 0; i < count; i++) {
            int chatUserId = this.packet.readInt();

            if (!ModToolReportInputGuard.isPrivateChatParticipant(chatUserId, reporterId, info.getId())) {
                return;
            }

            String chatMessage = ModToolReportInputGuard.normalize(this.packet.readString());
            if (!ModToolReportInputGuard.isValidChatLogMessage(chatMessage)) return;

            String username = chatUserId == info.getId()
                    ? info.getUsername()
                    : this.client.getUser().getUserInfo().getUsername();
            chatLogs.add(new ModToolChatLog(0, chatUserId, username, chatMessage));
        }

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

        message = ModToolReportInputGuard.withReporterContact(message, reporterName, reporterEmail);

        ModToolIssue issue = new ModToolIssue(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                userId,
                info.getUsername(),
                0,
                message,
                ModToolTicketType.IM);
        issue.category = category;
        issue.chatLogs = chatLogs;
        new InsertModToolIssue(issue).run();
        modTools.addTicket(issue);
        modTools.updateTicketToMods(issue);
    }
}
