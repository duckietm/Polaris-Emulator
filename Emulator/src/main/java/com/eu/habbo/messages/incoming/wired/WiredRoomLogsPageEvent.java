package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredRoomDiagnostics;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.wired.WiredRoomLogPageComposer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Official AIR 13 room-wide wired log page (3882) with the level, source and text filters of
 * {@code WiredRoomLogListView}.
 *
 * <p>The rows come from the room's wired diagnostics history, newest first (the room keeps the latest
 * 200). The level is AIR's number (0 info, 1 warn, 2 error, 3 debug) and the source is one of AIR's two
 * ({@code wiredmenu.logs_overview.log_source.N}): a "write to logs" box's line is source 1 "Wired" at
 * the level the box was set to and reads as just its message; the engine's own notes are source 0
 * "System". The requested page size is echoed, since the client drops a page of any other size.
 */
public class WiredRoomLogsPageEvent extends MessageHandler {
    private static final int MAX_PAGE_SIZE = 200;
    /** {@code wiredmenu.logs_overview.log_source.0}: the engine's own notes. */
    public static final int LOG_SOURCE_SYSTEM = 0;
    /** {@code wiredmenu.logs_overview.log_source.1}: a line a "write to logs" box wrote. */
    public static final int LOG_SOURCE_WIRED = 1;

    @Override
    public void handle() throws Exception {
        int page = this.packet.readInt();
        int pageSize = this.packet.readInt();
        int logLevelFilter = this.packet.readInt();
        int logSourceFilter = this.packet.readInt();
        String query = this.packet.readString();
        Room room = currentRoom();

        if (room == null || !room.canInspectWired(this.client.getHabbo())) {
            return;
        }

        int safePageSize = Math.clamp(pageSize, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(1, page);
        String normalizedQuery = (query != null) ? query.trim() : "";

        List<WiredRoomLogPageComposer.Entry> matches = collect(
                WiredManager.getDiagnosticsSnapshot(room.getId()), logLevelFilter, logSourceFilter, normalizedQuery);

        int totalEntries = matches.size();
        int firstIndex = (int) Math.min((long) (safePage - 1) * safePageSize, totalEntries);
        int lastIndex = Math.min(firstIndex + safePageSize, totalEntries);

        this.client.sendResponse(new WiredRoomLogPageComposer(
                totalEntries,
                safePage,
                pageSize,
                new ArrayList<>(matches.subList(firstIndex, lastIndex)),
                logLevelFilter,
                logSourceFilter,
                normalizedQuery));
    }

    static List<WiredRoomLogPageComposer.Entry> collect(
            WiredRoomDiagnostics.Snapshot snapshot, int logLevelFilter, int logSourceFilter, String query) {
        List<WiredRoomLogPageComposer.Entry> entries = new ArrayList<>();

        if (snapshot == null) {
            return entries;
        }

        String needle = query.toLowerCase(Locale.ROOT);
        List<WiredRoomDiagnostics.HistoryEntry> history = snapshot.getHistory();

        // The history is already newest first, like the official log list.
        for (WiredRoomDiagnostics.HistoryEntry entry : history) {
            int level = level(entry);
            int source = source(entry);

            if (logLevelFilter >= 0 && level != logLevelFilter) {
                continue;
            }

            if (logSourceFilter >= 0 && source != logSourceFilter) {
                continue;
            }

            String message = describe(entry);

            if (!needle.isEmpty() && !message.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }

            entries.add(new WiredRoomLogPageComposer.Entry(
                    entries.size() + 1L, level, source, message, entry.getOccurredAtMs()));
        }

        return entries;
    }

    static int source(WiredRoomDiagnostics.HistoryEntry entry) {
        return (entry.getType() == WiredRoomDiagnostics.Type.WIRED_LOG) ? LOG_SOURCE_WIRED : LOG_SOURCE_SYSTEM;
    }

    static int level(WiredRoomDiagnostics.HistoryEntry entry) {
        WiredRoomDiagnostics.Severity severity = entry.getSeverity();
        return (severity != null) ? severity.getLogLevel() : WiredRoomDiagnostics.Severity.INFO.getLogLevel();
    }

    private static String describe(WiredRoomDiagnostics.HistoryEntry entry) {
        if (entry.getType() == WiredRoomDiagnostics.Type.WIRED_LOG) {
            return entry.getReason();
        }

        StringBuilder message = new StringBuilder(entry.getType().name());

        if (entry.getSourceLabel() != null && !entry.getSourceLabel().isEmpty()) {
            message.append(" [").append(entry.getSourceLabel());

            if (entry.getSourceId() > 0) {
                message.append('#').append(entry.getSourceId());
            }

            message.append(']');
        }

        if (entry.getReason() != null && !entry.getReason().isEmpty()) {
            message.append(": ").append(entry.getReason());
        }

        return message.toString();
    }

    @Override
    public int getRatelimit() {
        return 250;
    }
}
