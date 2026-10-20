package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.wired.core.WiredRoomDiagnostics;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Our wired monitor packet (5101): the diagnostics thresholds and counters AIR has no packet for, the detail of the
 * latest occurrence of each diagnostic type ({@code type, reason, source label, source id}) and the history. The
 * official lines come in AIR's own packets: the room stats (1891, {@link WiredRoomStatsComposer}) and the error list
 * (15, {@link WiredErrorLogsComposer}).
 */
public class WiredMonitorDataComposer extends MessageComposer {
    private final WiredRoomDiagnostics.Snapshot snapshot;

    public WiredMonitorDataComposer(WiredRoomDiagnostics.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredMonitorDataComposer);

        if (this.snapshot == null) {
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendBoolean(false);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            this.response.appendInt(0);
            return this.response;
        }

        this.response.appendInt(this.snapshot.getUsageCurrentWindow());
        this.response.appendInt(this.snapshot.getUsageLimitPerWindow());
        this.response.appendBoolean(this.snapshot.isHeavy());
        this.response.appendInt(this.snapshot.getDelayedEventsPending());
        this.response.appendInt(this.snapshot.getDelayedEventsLimit());
        this.response.appendInt(this.snapshot.getAverageExecutionMs());
        this.response.appendInt(this.snapshot.getPeakExecutionMs());
        this.response.appendInt(this.snapshot.getRecursionDepthCurrent());
        this.response.appendInt(this.snapshot.getRecursionDepthLimit());
        this.response.appendInt(this.snapshot.getKilledRemainingSeconds());
        this.response.appendInt(this.snapshot.getUsageWindowMs());
        this.response.appendInt(this.snapshot.getOverloadAverageThresholdMs());
        this.response.appendInt(this.snapshot.getOverloadPeakThresholdMs());
        this.response.appendInt(this.snapshot.getHeavyUsageThresholdPercent());
        this.response.appendInt(this.snapshot.getHeavyConsecutiveWindowsThreshold());
        this.response.appendInt(this.snapshot.getOverloadConsecutiveWindowsThreshold());
        this.response.appendInt(this.snapshot.getHeavyDelayedThresholdPercent());
        this.response.appendInt(this.snapshot.getLogs().size());

        // The type, category, count and latest time of each error are AIR's error list (1132 asks, 15 answers,
        // WiredErrorLogsComposer); what is left here is the detail behind the latest occurrence of each type.
        for (WiredRoomDiagnostics.LogEntry log : this.snapshot.getLogs()) {
            this.response.appendString(log.getType().name());
            this.response.appendString(log.getLatestReason());
            this.response.appendString(log.getLatestSourceLabel());
            this.response.appendInt(BuildersClubItemIds.toWire(log.getLatestSourceId()));
        }

        this.response.appendInt(this.snapshot.getHistory().size());

        for (WiredRoomDiagnostics.HistoryEntry historyEntry : this.snapshot.getHistory()) {
            this.response.appendString(historyEntry.getType().name());
            this.response.appendString(historyEntry.getSeverity().name());
            this.response.appendInt((int) (historyEntry.getOccurredAtMs() / 1000L));
            this.response.appendString(historyEntry.getReason());
            this.response.appendString(historyEntry.getSourceLabel());
            this.response.appendInt(BuildersClubItemIds.toWire(historyEntry.getSourceId()));
        }

        return this.response;
    }
}
