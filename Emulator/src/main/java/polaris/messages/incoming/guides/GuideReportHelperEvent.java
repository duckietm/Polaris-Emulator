package polaris.messages.incoming.guides;

import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.guides.GuideTour;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolTicketType;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.guides.GuideSessionDetachedComposer;
import polaris.messages.outgoing.guides.GuideSessionEndedComposer;
import polaris.messages.outgoing.modtool.ModToolReportReceivedAlertComposer;

public class GuideReportHelperEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        String message = this.packet.readString();

        GuideTour tour = environment().getGuideManager().getActiveGuideTourByUser(this.client.getUser());

        if (tour == null) return;

        User reported = tour.getHelper();

        if (reported == this.client.getUser()) {
            reported = tour.getNoob();
        }

        // No helper yet (or one who declined): there is no one to report.
        if (reported == null) return;

        // One report per tour, which closes it: every report packet used to file a ticket and alert every moderator.
        if (!environment().getGuideManager().endReportedSession(tour)) return;

        ModToolIssue issue = new ModToolIssue(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                reported.getUserInfo().getId(),
                reported.getUserInfo().getUsername(),
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
