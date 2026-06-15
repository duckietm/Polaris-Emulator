package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.guides.GuardianTicket;
import com.eu.habbo.habbohotel.modtool.*;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.*;
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

        if (!ModToolReportInputGuard.isValidReportMessage(message) ||
                topic <= 0 ||
                (userId != -1 && !ModToolReportInputGuard.isPositiveId(userId)) ||
                !ModToolReportInputGuard.isPositiveId(roomId) ||
                userId == this.client.getHabbo().getHabboInfo().getId()) {
            return;
        }

        CfhTopic cfhTopic = Emulator.getGameEnvironment().getModToolManager().getCfhTopic(topic);

        if (cfhTopic == null) {
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
        List<ModToolIssue> issues = Emulator.getGameEnvironment().getModToolManager().openTicketsForHabbo(this.client.getHabbo());
        if (!issues.isEmpty()) {
            //this.client.sendResponse(new GenericAlertComposer("You've got still a pending ticket. Wait till the moderators are done reviewing your ticket."));
            this.client.sendResponse(new ReportRoomFormComposer(issues));
            return;
        }

        if (userId != -1) {
            Habbo reported = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

            if (reported != null) {
                if (cfhTopic != null && cfhTopic.action == CfhActionType.GUARDIANS && Emulator.getGameEnvironment().getGuideManager().activeGuardians()) {
                    GuardianTicket ticket = Emulator.getGameEnvironment().getGuideManager().getOpenReportedHabboTicket(reported);

                    if (ticket != null) {
                        this.client.sendResponse(new BullyReportedMessageComposer(BullyReportedMessageComposer.ALREADY_REPORTED));
                        return;
                    }

                    ArrayList<ModToolChatLog> chatLog = Emulator.getGameEnvironment().getModToolManager().getRoomChatlog(roomId);

                    if (chatLog.isEmpty()) {
                        this.client.sendResponse(new BullyReportedMessageComposer(BullyReportedMessageComposer.NO_CHAT));
                        return;
                    }

                    Emulator.getGameEnvironment().getGuideManager().addGuardianTicket(new GuardianTicket(this.client.getHabbo(), reported, chatLog));

                    this.client.sendResponse(new BullyReportedMessageComposer(BullyReportedMessageComposer.RECEIVED));
                } else {
                    ModToolIssue issue = new ModToolIssue(this.client.getHabbo().getHabboInfo().getId(), this.client.getHabbo().getHabboInfo().getUsername(), reported.getHabboInfo().getId(), reported.getHabboInfo().getUsername(), roomId, message, ModToolTicketType.NORMAL);
                    issue.category = topic;
                    new InsertModToolIssue(issue).run();

                    Emulator.getGameEnvironment().getModToolManager().addTicket(issue);
                    Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);
                    this.client.sendResponse(new ModToolReportReceivedAlertComposer(ModToolReportReceivedAlertComposer.REPORT_RECEIVED, cfhTopic.reply));

                    if (cfhTopic != null) {
                        if (cfhTopic.action != CfhActionType.MODS) {
                            Emulator.getThreading().run(() -> {
                                if (issue.state == ModToolTicketState.OPEN) {
                                    if (cfhTopic.action == CfhActionType.AUTO_IGNORE) {
                                        if (ReportEvent.this.client.getHabbo().getHabboStats().ignoreUser(ReportEvent.this.client, reported.getHabboInfo().getId())) {
                                            ReportEvent.this.client.sendResponse(new RoomUserIgnoredComposer(reported, RoomUserIgnoredComposer.IGNORED));
                                        }
                                    }

                                    ReportEvent.this.client.sendResponse(new ModToolIssueHandledComposer(cfhTopic.reply).compose());
                                    Emulator.getGameEnvironment().getModToolManager().closeTicketAsHandled(issue, null);
                                }
                            }, 30 * 1000);
                        }
                    }
                }
            }
        } else {
            ModToolIssue issue = new ModToolIssue(this.client.getHabbo().getHabboInfo().getId(), this.client.getHabbo().getHabboInfo().getUsername(), room != null ? room.getOwnerId() : 0, room != null ? room.getOwnerName() : "", roomId, message, ModToolTicketType.ROOM);
            issue.category = topic;
            new InsertModToolIssue(issue).run();

            this.client.sendResponse(new ModToolReportReceivedAlertComposer(ModToolReportReceivedAlertComposer.REPORT_RECEIVED, message));
            Emulator.getGameEnvironment().getModToolManager().addTicket(issue);
            Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);

            if (cfhTopic != null) {
                if (cfhTopic.action != CfhActionType.MODS) {
                    Emulator.getThreading().run(() -> {
                        if (issue.state == ModToolTicketState.OPEN) {
                            if (cfhTopic.action == CfhActionType.AUTO_IGNORE) {
                                if (ReportEvent.this.client.getHabbo().getHabboStats().ignoreUser(ReportEvent.this.client, issue.reportedId)) {
                                    Habbo reported = Emulator.getGameEnvironment().getHabboManager().getHabbo(issue.reportedId);
                                    if (reported != null) {
                                        ReportEvent.this.client.sendResponse(new RoomUserIgnoredComposer(reported, RoomUserIgnoredComposer.IGNORED));
                                    }
                                }
                            }

                            ReportEvent.this.client.sendResponse(new ModToolIssueHandledComposer(cfhTopic.reply).compose());
                            Emulator.getGameEnvironment().getModToolManager().closeTicketAsHandled(issue, null);
                        }
                    }, 30 * 1000);
                }
            }

        }
    }
}
