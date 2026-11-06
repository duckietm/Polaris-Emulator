package polaris.messages.outgoing.wired;

import java.util.List;
import polaris.hotel.wired.core.WiredErrorLogs;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Official AIR WiredErrorLogs (15), the answer to the monitor's error log request (1132) and to its clear (1632):
 * {@code int count}, then per row {@code int errorId, string name, string category, int count, long ms since the
 * latest} ({@link WiredErrorLogs}).
 */
public class WiredErrorLogsComposer extends MessageComposer {
    private final List<WiredErrorLogs.Row> rows;

    public WiredErrorLogsComposer(List<WiredErrorLogs.Row> rows) {
        this.rows = (rows != null) ? List.copyOf(rows) : List.of();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredErrorLogsComposer);
        this.response.appendInt(this.rows.size());

        for (WiredErrorLogs.Row row : this.rows) {
            this.response.appendInt(row.errorId());
            this.response.appendString(row.name());
            this.response.appendString(row.category());
            this.response.appendInt(row.count());
            WiredVariableHoldersPageComposer.appendLong(this.response, row.msSinceLatest());
        }

        return this.response;
    }
}
