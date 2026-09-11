package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Official AIR 13 {@code WiredUserVariablesPage}: one page of the holders of a single wired
 * variable, echoing back the filters the request carried.
 */
public class WiredVariableHoldersPageComposer extends MessageComposer {
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final String variableId;
    private final int totalEntries;
    private final int currentPage;
    private final int amount;
    private final List<RoomWiredVariableCatalog.Holder> elements;
    private final int userTypeFilter;
    private final int sortTypeFilter;

    public WiredVariableHoldersPageComposer(
            String variableId,
            int totalEntries,
            int currentPage,
            int amount,
            List<RoomWiredVariableCatalog.Holder> elements,
            int userTypeFilter,
            int sortTypeFilter) {
        this.variableId = variableId;
        this.totalEntries = totalEntries;
        this.currentPage = currentPage;
        this.amount = amount;
        this.elements = (elements != null) ? elements : Collections.emptyList();
        this.userTypeFilter = userTypeFilter;
        this.sortTypeFilter = sortTypeFilter;
    }

    /** The wire has no 64-bit primitive: a long travels as its high and low 32-bit halves. */
    static void appendLong(ServerMessage message, long value) {
        message.appendInt((int) (value >>> 32));
        message.appendInt((int) value);
    }

    static String formatTimestamp(long millis) {
        return (millis > 0L) ? TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(millis)) : "";
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredVariableHoldersPageComposer);
        this.response.appendString(this.variableId);
        this.response.appendInt(this.totalEntries);
        this.response.appendInt(this.currentPage);
        this.response.appendInt(this.amount);

        this.response.appendInt(this.elements.size());
        for (RoomWiredVariableCatalog.Holder holder : this.elements) {
            this.response.appendInt(holder.getEntityType());
            this.response.appendInt(holder.getEntityId());
            this.response.appendString(holder.getEntityName());
            this.response.appendInt(holder.getValue());
            appendLong(this.response, holder.getCreatedAt());
            this.response.appendString(formatTimestamp(holder.getCreatedAt()));
            appendLong(this.response, holder.getUpdatedAt());
            this.response.appendString(formatTimestamp(holder.getUpdatedAt()));
        }

        this.response.appendInt(this.userTypeFilter);
        this.response.appendInt(this.sortTypeFilter);

        return this.response;
    }
}
