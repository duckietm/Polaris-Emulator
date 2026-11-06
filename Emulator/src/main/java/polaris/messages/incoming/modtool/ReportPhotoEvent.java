package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.items.interactions.InteractionExternalImage;
import polaris.hotel.modtool.CfhTopic;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.modtool.ModToolTicketType;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModToolReportReceivedAlertComposer;
import polaris.threading.runnables.InsertModToolIssue;

public class ReportPhotoEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return ModToolReportGate.RATE_LIMIT_MS;
    }

    @Override
    public void handle() throws Exception {
        ModToolManager modTools = Emulator.getGameEnvironment().getModToolManager();
        if (ModToolReportGate.refuse(this.client, modTools)) return;

        boolean hasExtradataId = this.packet.readShort() != 0;

        this.packet.getBuffer().resetReaderIndex();

        if (hasExtradataId) {
            this.packet.readString();
        }

        int roomId = this.packet.readInt();
        this.packet.readInt();
        int topicId = this.packet.readInt();
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
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

        if (!ModToolReportInputGuard.isPositiveId(roomId)
                || !ModToolReportInputGuard.isPositiveId(topicId)
                || !ModToolReportInputGuard.isPositiveId(itemId)) {
            return;
        }

        CfhTopic topic = modTools.getCfhTopic(topicId);

        if (topic == null) return;

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

        if (room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (item == null || !(item instanceof InteractionExternalImage)) return;

        UserInfo photoOwner = Emulator.getGameEnvironment().getUserManager().getUserInfo(item.getUserId());

        if (photoOwner == null) return;

        ModToolIssue issue = new ModToolIssue(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                photoOwner.getId(),
                photoOwner.getUsername(),
                roomId,
                ModToolReportInputGuard.withReporterContact("", reporterName, reporterEmail),
                ModToolTicketType.PHOTO);
        issue.photoItem = item;
        issue.category = topicId;

        new InsertModToolIssue(issue).run();

        this.client.sendResponse(
                new ModToolReportReceivedAlertComposer(ModToolReportReceivedAlertComposer.REPORT_RECEIVED, ""));
        modTools.addTicket(issue);
        modTools.updateTicketToMods(issue);
    }
}
