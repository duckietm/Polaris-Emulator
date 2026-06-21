package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guides.GuardianTicket;
import com.eu.habbo.habbohotel.modtool.ModToolChatLog;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.BullyReportedMessageComposer;
import com.eu.habbo.messages.outgoing.modtool.HelperRequestDisabledComposer;

import java.util.ArrayList;

public class ReportBullyEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().getHabboStats().allowTalk()) {
            this.client.sendResponse(new HelperRequestDisabledComposer());
            return;
        }

        int userId = this.packet.readInt();
        int roomId = this.packet.readInt();

        if (!ModToolReportInputGuard.isPositiveId(userId) ||
                !ModToolReportInputGuard.isPositiveId(roomId) ||
                userId == this.client.getHabbo().getHabboInfo().getId()) {
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

        if (room != null) {
            Habbo habbo = room.getHabbo(userId);

            if (habbo != null) {
                GuardianTicket ticket = Emulator.getGameEnvironment().getGuideManager().getOpenReportedHabboTicket(habbo);

                if (ticket != null) {
                    this.client.sendResponse(new BullyReportedMessageComposer(BullyReportedMessageComposer.ALREADY_REPORTED));
                    return;
                }

                ArrayList<ModToolChatLog> chatLog = Emulator.getGameEnvironment().getModToolManager().getRoomChatlog(roomId);

                if (chatLog.isEmpty()) {
                    this.client.sendResponse(new BullyReportedMessageComposer(BullyReportedMessageComposer.NO_CHAT));
                    return;
                }

                Emulator.getGameEnvironment().getGuideManager().addGuardianTicket(new GuardianTicket(this.client.getHabbo(), habbo, chatLog));

                this.client.sendResponse(new BullyReportedMessageComposer(BullyReportedMessageComposer.RECEIVED));
            }
        }
    }
}
