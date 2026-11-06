package polaris.messages.incoming.modtool;

import java.util.ArrayList;
import polaris.Emulator;
import polaris.hotel.guides.GuardianTicket;
import polaris.hotel.modtool.ModToolChatLog;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.BullyReportedMessageComposer;
import polaris.messages.outgoing.modtool.HelperRequestDisabledComposer;

public class ReportBullyEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().getUserStats().allowTalk()) {
            this.client.sendResponse(new HelperRequestDisabledComposer());
            return;
        }

        int userId = this.packet.readInt();
        int roomId = this.packet.readInt();

        if (!ModToolReportInputGuard.isPositiveId(userId)
                || !ModToolReportInputGuard.isPositiveId(roomId)
                || userId == this.client.getUser().getUserInfo().getId()) {
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

        if (room != null) {
            User user = room.getUser(userId);

            if (user != null) {
                GuardianTicket ticket =
                        Emulator.getGameEnvironment().getGuideManager().getOpenReportedUserTicket(user);

                if (ticket != null) {
                    this.client.sendResponse(
                            new BullyReportedMessageComposer(BullyReportedMessageComposer.ALREADY_REPORTED));
                    return;
                }

                ArrayList<ModToolChatLog> chatLog =
                        Emulator.getGameEnvironment().getModToolManager().getRoomChatlog(roomId);

                if (chatLog.isEmpty()) {
                    this.client.sendResponse(new BullyReportedMessageComposer(BullyReportedMessageComposer.NO_CHAT));
                    return;
                }

                Emulator.getGameEnvironment()
                        .getGuideManager()
                        .addGuardianTicket(new GuardianTicket(this.client.getUser(), user, chatLog));

                this.client.sendResponse(new BullyReportedMessageComposer(BullyReportedMessageComposer.RECEIVED));
            }
        }
    }
}
