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
 * <p>The rows come from the room's wired diagnostics history: the severity is the log level and the
 * diagnostic type is the log source, so the client's two dropdowns map straight onto them.
 */
public class WiredRoomLogsPageEvent extends MessageHandler {
    private static final int MAX_PAGE_SIZE = 200;

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
        int firstIndex = Math.min((safePage - 1) * safePageSize, totalEntries);
        int lastIndex = Math.min(firstIndex + safePageSize, totalEntries);

        this.client.sendResponse(new WiredRoomLogPageComposer(
                totalEntries,
                safePage,
                safePageSize,
                new ArrayList<>(matches.subList(firstIndex, lastIndex)),
                logLevelFilter,
                logSourceFilter,
                normalizedQuery));
    }

    private static List<WiredRoomLogPageComposer.Entry> collect(
            WiredRoomDiagnostics.Snapshot snapshot, int logLevelFilter, int logSourceFilter, String query) {
        List<WiredRoomLogPageComposer.Entry> entries = new ArrayList<>();

        if (snapshot == null) {
            return entries;
        }

        String needle = query.toLowerCase(Locale.ROOT);
        List<WiredRoomDiagnostics.HistoryEntry> history = snapshot.getHistory();

        // Newest first, like the official log list.
        for (int index = history.size() - 1; index >= 0; index--) {
            WiredRoomDiagnostics.HistoryEntry entry = history.get(index);
            int level = entry.getSeverity().ordinal();
            int source = entry.getType().ordinal();

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

    private static String describe(WiredRoomDiagnostics.HistoryEntry entry) {
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
