package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collections;
import java.util.List;

/**
 * Official AIR 13 {@code WiredLogPage}: one page of the room-wide wired log, plus the filters that
 * produced it so the client can restore its own controls.
 */
public class WiredRoomLogPageComposer extends MessageComposer {

    /** One log line as the official {@code WiredLogEntry} reads it. */
    public static final class Entry {
        private final long id;
        private final int logLevel;
        private final int logSource;
        private final String logMessage;
        private final long timestamp;

        public Entry(long id, int logLevel, int logSource, String logMessage, long timestamp) {
            this.id = id;
            this.logLevel = logLevel;
            this.logSource = logSource;
            this.logMessage = (logMessage != null) ? logMessage : "";
            this.timestamp = timestamp;
        }

        public long getId() {
            return this.id;
        }

        public int getLogLevel() {
            return this.logLevel;
        }

        public int getLogSource() {
            return this.logSource;
        }

        public String getLogMessage() {
            return this.logMessage;
        }

        public long getTimestamp() {
            return this.timestamp;
        }
    }

    private final int totalEntries;
    private final int currentPage;
    private final int amount;
    private final List<Entry> entries;
    private final int logLevelFilter;
    private final int logSourceFilter;
    private final String query;

    public WiredRoomLogPageComposer(
            int totalEntries,
            int currentPage,
            int amount,
            List<Entry> entries,
            int logLevelFilter,
            int logSourceFilter,
            String query) {
        this.totalEntries = totalEntries;
        this.currentPage = currentPage;
        this.amount = amount;
        this.entries = (entries != null) ? entries : Collections.emptyList();
        this.logLevelFilter = logLevelFilter;
        this.logSourceFilter = logSourceFilter;
        this.query = query;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredRoomLogPageComposer);
        this.response.appendInt(this.totalEntries);
        this.response.appendInt(this.currentPage);
        this.response.appendInt(this.amount);

        this.response.appendInt(this.entries.size());
        for (Entry entry : this.entries) {
            WiredVariableHoldersPageComposer.appendLong(this.response, entry.getId());
            this.response.appendByte(entry.getLogLevel());
            this.response.appendByte(entry.getLogSource());
            this.response.appendString(entry.getLogMessage());
            WiredVariableHoldersPageComposer.appendLong(this.response, entry.getTimestamp());
            this.response.appendString(WiredVariableHoldersPageComposer.formatTimestamp(entry.getTimestamp()));
        }

        boolean hasLogLevelFilter = this.logLevelFilter >= 0;
        this.response.appendBoolean(hasLogLevelFilter);
        if (hasLogLevelFilter) {
            this.response.appendByte(this.logLevelFilter);
        }

        boolean hasLogSourceFilter = this.logSourceFilter >= 0;
        this.response.appendBoolean(hasLogSourceFilter);
        if (hasLogSourceFilter) {
            this.response.appendByte(this.logSourceFilter);
        }

        boolean hasQuery = (this.query != null) && !this.query.isEmpty();
        this.response.appendBoolean(hasQuery);
        if (hasQuery) {
            this.response.appendString(this.query);
        }

        return this.response;
    }
}
