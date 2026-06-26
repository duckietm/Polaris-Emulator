package com.eu.habbo.messages.incoming.guides;

import com.eu.habbo.Emulator;
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

        GuideTour tour = Emulator.getGameEnvironment().getGuideManager().getGuideTourByHabbo(this.client.getHabbo());

        if (tour != null) {
            Habbo reported = tour.getHelper();

            if (reported == this.client.getHabbo()) {
                reported = tour.getNoob();
            }

            var issue = new ModToolIssue(this.client.getHabbo().getHabboInfo().getId(),
                    this.client.getHabbo().getHabboInfo().getUsername(),
                    reported.getHabboInfo().getId(),
                    reported.getHabboInfo().getUsername(),
                    0,
                    message,
                    ModToolTicketType.GUIDE_SYSTEM);


            Emulator.getGameEnvironment().getModToolManager().addTicket(issue);
            Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);
            this.client.sendResponse(new ModToolReportReceivedAlertComposer(ModToolReportReceivedAlertComposer.REPORT_RECEIVED, message));

            this.client.sendResponse(new GuideSessionDetachedComposer());
            this.client.sendResponse(new GuideSessionEndedComposer(GuideSessionEndedComposer.HELP_CASE_CLOSED));

            reported.getClient().sendResponse(new GuideSessionDetachedComposer());
            reported.getClient().sendResponse(new GuideSessionEndedComposer(GuideSessionEndedComposer.HELP_CASE_CLOSED));
        }
    }
}
