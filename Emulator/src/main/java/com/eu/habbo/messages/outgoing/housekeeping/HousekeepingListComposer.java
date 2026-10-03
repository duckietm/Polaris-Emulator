package com.eu.habbo.messages.outgoing.housekeeping;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * One housekeeping list as a table: the list key and target it answers, a
 * result, the column keys, then every row as one string per column. The client
 * names the columns from its texts and formats the time columns itself.
 */
public class HousekeepingListComposer extends MessageComposer {
    private final String listKey;
    private final int targetId;
    private final boolean ok;
    private final String message;
    private final List<String> columns;
    private final List<List<String>> rows;

    public HousekeepingListComposer(
            String listKey, int targetId, boolean ok, String message, List<String> columns, List<List<String>> rows) {
        this.listKey = listKey;
        this.targetId = targetId;
        this.ok = ok;
        this.message = message;
        this.columns = columns;
        this.rows = rows;
    }

    public static HousekeepingListComposer failure(String listKey, int targetId, String message) {
        return new HousekeepingListComposer(listKey, targetId, false, message, List.of(), List.of());
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HousekeepingListComposer);
        this.response.appendString(safe(this.listKey));
        this.response.appendInt(this.targetId);
        this.response.appendBoolean(this.ok);
        this.response.appendString(safe(this.message));
        this.response.appendInt(this.columns.size());

        for (String column : this.columns) {
            this.response.appendString(safe(column));
        }

        this.response.appendInt(this.rows.size());

        for (List<String> row : this.rows) {
            for (int i = 0; i < this.columns.size(); i++) {
                this.response.appendString(i < row.size() ? safe(row.get(i)) : "");
            }
        }

        return this.response;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
