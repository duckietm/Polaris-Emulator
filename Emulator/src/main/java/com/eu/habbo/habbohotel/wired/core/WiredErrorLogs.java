package com.eu.habbo.habbohotel.wired.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The error list of the wired menu monitor, as Habbo's WiredErrorLogs (answer 15 to the request 1132): always the same
 * ten rows in Habbo's order, each {@code errorId, name, category, count, ms since the latest}. The client picks the info
 * text by the id ({@code wiredmenu.error_info.<id>}) and the row icon by the category ({@code icon_wired_error} /
 * {@code icon_wired_warning}); placement failure and the variable fx limit share id 8, as on Habbo. A row the room has
 * not hit has count 0 and {@code -1} ms.
 *
 * <p>The counts come from the room's wired diagnostics: too many variables when a furni or user holds as many
 * variables as one holder may ({@link WiredVariableHolderLimit}), transaction failure when a wired transaction fails
 * on the room's or the server's side, the variable fx limit when the room holds more fx boxes than are drawn. Our own diagnostics that Habbo does not list (an effect without
 * targets, an unreachable furni, a "write to logs" line) follow the ten rows with ids from {@link #FIRST_OWN_ERROR_ID}.
 */
public final class WiredErrorLogs {

    public static final String CATEGORY_ERROR = "ERROR";
    public static final String CATEGORY_WARNING = "WARNING";

    /** Ids of the rows this hotel adds after Habbo's ten; far above Habbo's so its info texts are never picked. */
    public static final int FIRST_OWN_ERROR_ID = 100;

    /** Habbo's ten rows, in order. */
    private static final List<Definition> OFFICIAL = List.of(
            new Definition(0, "EXECUTION_CAP", CATEGORY_ERROR, WiredRoomDiagnostics.Type.EXECUTION_CAP),
            new Definition(1, "DELAYED_EVENTS_CAP", CATEGORY_ERROR, WiredRoomDiagnostics.Type.DELAYED_EVENTS_CAP),
            new Definition(2, "EXECUTOR_OVERLOAD", CATEGORY_ERROR, WiredRoomDiagnostics.Type.EXECUTOR_OVERLOAD),
            new Definition(3, "MARKED_AS_HEAVY", CATEGORY_WARNING, WiredRoomDiagnostics.Type.MARKED_AS_HEAVY),
            new Definition(4, "KILLED", CATEGORY_ERROR, WiredRoomDiagnostics.Type.KILLED),
            new Definition(5, "RECURSION_TIMEOUT", CATEGORY_ERROR, WiredRoomDiagnostics.Type.RECURSION_TIMEOUT),
            new Definition(6, "TOO_MANY_VARIABLES", CATEGORY_ERROR, WiredRoomDiagnostics.Type.TOO_MANY_VARIABLES),
            new Definition(7, "TRANSACTION_FAILURE", CATEGORY_ERROR, WiredRoomDiagnostics.Type.TRANSACTION_FAILURE),
            new Definition(8, "PLACEMENT_FAILURE", CATEGORY_ERROR, WiredRoomDiagnostics.Type.PLACEMENT_FAILED),
            new Definition(8, "VARIABLE_FX_LIMIT", CATEGORY_ERROR, WiredRoomDiagnostics.Type.VARIABLE_FX_LIMIT));

    /** Our own rows after Habbo's: the name is the diagnostic type, the category its severity. */
    private static final List<WiredRoomDiagnostics.Type> OWN = List.of(
            WiredRoomDiagnostics.Type.NO_TARGETS,
            WiredRoomDiagnostics.Type.UNREACHABLE,
            WiredRoomDiagnostics.Type.WIRED_LOG);

    /** One row of the list. {@code msSinceLatest} is {@code -1} while {@code count} is 0. */
    public record Row(int errorId, String name, String category, int count, long msSinceLatest) {}

    private record Definition(int errorId, String name, String category, WiredRoomDiagnostics.Type source) {}

    private WiredErrorLogs() {}

    /** The rows of a room's diagnostics at {@code now}; without diagnostics every row is at zero. */
    public static List<Row> rows(WiredRoomDiagnostics.Snapshot snapshot, long now) {
        Map<WiredRoomDiagnostics.Type, WiredRoomDiagnostics.LogEntry> entries =
                new EnumMap<>(WiredRoomDiagnostics.Type.class);

        if (snapshot != null) {
            for (WiredRoomDiagnostics.LogEntry entry : snapshot.getLogs()) {
                entries.put(entry.getType(), entry);
            }
        }

        List<Row> rows = new ArrayList<>(OFFICIAL.size() + OWN.size());

        for (Definition definition : OFFICIAL) {
            rows.add(row(
                    definition.errorId(),
                    definition.name(),
                    definition.category(),
                    definition.source() == null ? null : entries.get(definition.source()),
                    now));
        }

        int ownId = FIRST_OWN_ERROR_ID;

        for (WiredRoomDiagnostics.Type type : OWN) {
            WiredRoomDiagnostics.LogEntry entry = entries.get(type);
            String category =
                    entry == null ? CATEGORY_WARNING : entry.getSeverity().name();

            rows.add(row(ownId++, type.name(), category, entry, now));
        }

        return rows;
    }

    private static Row row(int errorId, String name, String category, WiredRoomDiagnostics.LogEntry entry, long now) {
        int count = entry == null ? 0 : Math.max(0, entry.getCount());
        long msSinceLatest = count > 0 ? Math.max(0L, now - entry.getLastOccurredAtMs()) : -1L;

        return new Row(errorId, name, category, count, msSinceLatest);
    }
}
