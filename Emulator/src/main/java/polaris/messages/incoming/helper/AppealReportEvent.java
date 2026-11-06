package polaris.messages.incoming.helper;

import polaris.Emulator;
import polaris.hotel.modtool.MyReportStatus;
import polaris.hotel.modtool.MyReportStatusRepository;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.MyReportsStatusComposer;

/**
 * AIR 13 composer 3063 (`MyReportStatus.onClickAppeal`): the player disputes the outcome
 * of one of their own reports. The official button is only enabled on a report that was
 * decided, ended without a sanction and has not been appealed yet, and the server checks
 * the same three things before filing the appeal.
 *
 * <p>Filing it marks the ticket appealed and tells the staff on duty, then the refreshed
 * list goes back so the window redraws with the appeal pending.
 */
public class AppealReportEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        int reportId = this.packet.readInt();

        if (reportId <= 0) return;

        int userId = this.client.getUser().getUserInfo().getId();
        MyReportStatus report = MyReportStatusRepository.findByReporterAndId(userId, reportId);

        if (report == null || !report.canBeAppealed()) return;

        if (!MyReportStatusRepository.appeal(userId, reportId, Emulator.getIntUnixTimestamp())) return;

        Emulator.getGameEnvironment()
                .getUserManager()
                .staffAlert(Emulator.getTexts()
                        .getValue("help.report.appeal.staffalert", "%username% appealed report #%id%.")
                        .replace(
                                "%username%",
                                this.client.getUser().getUserInfo().getUsername())
                        .replace("%id%", Integer.toString(reportId)));

        this.client.sendResponse(new MyReportsStatusComposer(MyReportStatusRepository.findByReporter(userId)));
    }
}
