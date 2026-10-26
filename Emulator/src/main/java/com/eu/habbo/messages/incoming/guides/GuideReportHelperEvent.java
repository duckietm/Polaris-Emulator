package com.eu.habbo.messages.incoming.guides;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.guides.GuideTour;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.guides.GuideSessionDetachedComposer;
import com.eu.habbo.messages.outgoing.guides.GuideSessionEndedComposer;
import com.eu.habbo.messages.outgoing.modtool.ModToolReportReceivedAlertComposer;

public class GuideReportHelperEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String message = this.packet.readString();

        GuideTour tour = environment().getGuideManager().getActiveGuideTourByHabbo(this.client.getHabbo());

        if (tour == null) return;

        Habbo reported = tour.getHelper();

        if (reported == this.client.getHabbo()) {
            reported = tour.getNoob();
        }

        // No helper yet (or one who declined): there is no one to report.
        if (reported == null) return;

        // One report per tour, which closes it: every report packet used to file a ticket and alert every moderator.
        if (!environment().getGuideManager().endReportedSession(tour)) return;

        ModToolIssue issue = new ModToolIssue(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                reported.getHabboInfo().getId(),
                reported.getHabboInfo().getUsername(),
                0,
                message,
                ModToolTicketType.GUIDE_SYSTEM);

        environment().getModToolManager().addTicket(issue);
        environment().getModToolManager().updateTicketToMods(issue);
        this.client.sendResponse(
                new ModToolReportReceivedAlertComposer(ModToolReportReceivedAlertComposer.REPORT_RECEIVED, message));

        this.client.sendResponse(new GuideSessionDetachedComposer());
        this.client.sendResponse(new GuideSessionEndedComposer(GuideSessionEndedComposer.HELP_CASE_CLOSED));

        GameClient reportedClient = reported.getClient();

        if (reportedClient != null) {
            reportedClient.sendResponse(new GuideSessionDetachedComposer());
            reportedClient.sendResponse(new GuideSessionEndedComposer(GuideSessionEndedComposer.HELP_CASE_CLOSED));
        }
    }

    private static GameEnvironment environment() {
        return Emulator.getGameEnvironment();
    }
}
