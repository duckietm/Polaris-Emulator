package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.habbohotel.modtool.MyReportStatus;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * AIR 13 event 2981 (`HabboHelp.onMyCfhReportStatusMessageEvent` ->
 * `MyReportStatus.openWindow`): the reports this player filed, with the decision and the
 * appeal state of each. Field order follows the official reader exactly.
 */
public class MyReportsStatusComposer extends MessageComposer {
    private final List<MyReportStatus> reports;

    public MyReportsStatusComposer(List<MyReportStatus> reports) {
        this.reports = reports == null ? List.of() : List.copyOf(reports);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MyReportsStatusComposer);
        this.response.appendInt(this.reports.size());

        for (MyReportStatus report : this.reports) {
            appendLong(this.response, report.id());
            appendLong(this.response, report.creationTime());
            this.response.appendString(report.userMessage());
            this.response.appendInt(report.userCategory());
            this.response.appendString(report.reportedAccountName());
            appendLong(this.response, report.closeTime());
            this.response.appendBoolean(report.sanctioned());
            this.response.appendBoolean(report.sanctionGivenByAutoModeration());
            this.response.appendByte(report.appealStatus());
            appendLong(this.response, report.appealCreationTime());
            appendLong(this.response, report.appealResolutionTime());
        }

        return this.response;
    }

    public List<MyReportStatus> getReports() {
        return this.reports;
    }

    /** Flash {@code readLong()} wire shape (two big-endian ints). */
    private static void appendLong(ServerMessage message, long value) {
        message.appendInt((int) (value >>> 32));
        message.appendInt((int) value);
    }
}
