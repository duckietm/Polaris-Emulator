package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guides.GuardianTicket;
import com.eu.habbo.habbohotel.modtool.CfhActionType;
import com.eu.habbo.habbohotel.modtool.CfhTopic;
import com.eu.habbo.habbohotel.modtool.ModToolChatLog;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketState;
import com.eu.habbo.habbohotel.modtool.ModToolTicketType;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.BullyReportedMessageComposer;
import com.eu.habbo.messages.outgoing.modtool.HelperRequestDisabledComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolIssueHandledComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolReportReceivedAlertComposer;
import com.eu.habbo.messages.outgoing.modtool.ReportRoomFormComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;
import com.eu.habbo.threading.runnables.InsertModToolIssue;
import java.util.ArrayList;
import java.util.List;

public class ReportEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().getHabboStats().allowTalk()) {
            this.client.sendResponse(new HelperRequestDisabledComposer());
            return;
        }

        String message = ModToolReportInputGuard.normalize(this.packet.readString());
        int topic = this.packet.readInt();
        int userId = this.packet.readInt();
        int roomId = this.packet.readInt();
        this.packet.readInt();
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

        if (!ModToolReportInputGuard.isValidReportMessage(message)
                || topic <= 0
                || (userId != -1 && !ModToolReportInputGuard.isPositiveId(userId))
                || !ModToolReportInputGuard.isPositiveId(roomId)
                || userId == this.client.getHabbo().getHabboInfo().getId()) {
            return;
        }

        message = ModToolReportInputGuard.withReporterContact(message, reporterName, reporterEmail);

        CfhTopic cfhTopic = Emulator.getGameEnvironment().getModToolManager().getCfhTopic(topic);

        if (cfhTopic == null) {
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
        List<ModToolIssue> issues =
                Emulator.getGameEnvironment().getModToolManager().openTicketsForHabbo(this.client.getHabbo());
        if (!issues.isEmpty()) {
            // this.client.sendResponse(new GenericAlertComposer("You've got still a pending ticket. Wait till the
            // moderators are done reviewing your ticket."));
            this.client.sendResponse(new ReportRoomFormComposer(issues));
            return;
        }

        if (userId != -1) {
            this.reportUser(cfhTopic, topic, userId, roomId, message);
        } else {
            this.reportRoom(cfhTopic, topic, room, roomId, message);
        }
    }

    /** A report of a user: to the guardians when the topic asks for them and some are on duty, else a ticket. */
    private void reportUser(CfhTopic cfhTopic, int topic, int userId, int roomId, String message) {
        Habbo reported = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (reported == null) return;

        if (cfhTopic.action == CfhActionType.GUARDIANS
                && Emulator.getGameEnvironment().getGuideManager().activeGuardians()) {
            this.reportToGuardians(reported, roomId);
            return;
        }

        ModToolIssue issue = new ModToolIssue(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                reported.getHabboInfo().getId(),
                reported.getHabboInfo().getUsername(),
                roomId,
                message,
                ModToolTicketType.NORMAL);
        issue.category = topic;
        new InsertModToolIssue(issue).run();

        Emulator.getGameEnvironment().getModToolManager().addTicket(issue);
        Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);
        this.client.sendResponse(new ModToolReportReceivedAlertComposer(
                ModToolReportReceivedAlertComposer.REPORT_RECEIVED, cfhTopic.reply));

        this.scheduleAutomaticAnswer(cfhTopic, issue);
    }

    /** Opens a guardian case with the room's chat, unless one is open for the user or there is no chat. */
    private void reportToGuardians(Habbo reported, int roomId) {
        GuardianTicket ticket = Emulator.getGameEnvironment().getGuideManager().getOpenReportedHabboTicket(reported);

        if (ticket != null) {
            this.client.sendResponse(new BullyReportedMessageComposer(BullyReportedMessageComposer.ALREADY_REPORTED));
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
                .addGuardianTicket(new GuardianTicket(this.client.getHabbo(), reported, chatLog));

        this.client.sendResponse(new BullyReportedMessageComposer(BullyReportedMessageComposer.RECEIVED));
    }

    /** A report of a room opens a ticket against its owner. */
    private void reportRoom(CfhTopic cfhTopic, int topic, Room room, int roomId, String message) {
        ModToolIssue issue = new ModToolIssue(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                room != null ? room.getOwnerId() : 0,
                room != null ? room.getOwnerName() : "",
                roomId,
                message,
                ModToolTicketType.ROOM);
        issue.category = topic;
        new InsertModToolIssue(issue).run();

        // The topic's reply, as a user report answers: this used to echo the reporter's own message back.
        this.client.sendResponse(new ModToolReportReceivedAlertComposer(
                ModToolReportReceivedAlertComposer.REPORT_RECEIVED, cfhTopic.reply));
        Emulator.getGameEnvironment().getModToolManager().addTicket(issue);
        Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);

        this.scheduleAutomaticAnswer(cfhTopic, issue);
    }

    /**
     * A topic the moderators do not handle themselves is answered 30 seconds later, when the ticket is still open:
     * the reported user is ignored for an auto-ignore topic, the topic's reply is shown and the ticket is closed.
     */
    private void scheduleAutomaticAnswer(CfhTopic cfhTopic, ModToolIssue issue) {
        if (cfhTopic.action == CfhActionType.MODS) return;

        Emulator.getThreading()
                .run(
                        () -> {
                            if (issue.state != ModToolTicketState.OPEN) return;

                            if (cfhTopic.action == CfhActionType.AUTO_IGNORE
                                    && this.client
                                            .getHabbo()
                                            .getHabboStats()
                                            .ignoreUser(this.client, issue.reportedId)) {
                                Habbo reported = Emulator.getGameEnvironment()
                                        .getHabboManager()
                                        .getHabbo(issue.reportedId);
                                if (reported != null) {
                                    this.client.sendResponse(
                                            new RoomUserIgnoredComposer(reported, RoomUserIgnoredComposer.IGNORED));
                                }
                            }

                            this.client.sendResponse(new ModToolIssueHandledComposer(cfhTopic.reply).compose());
                            Emulator.getGameEnvironment().getModToolManager().closeTicketAsHandled(issue, null);
                        },
                        30 * 1000);
    }
}
