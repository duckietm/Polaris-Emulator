package com.eu.habbo.messages.outgoing.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.habbohotel.wired.core.WiredErrorLogs;
import com.eu.habbo.habbohotel.wired.core.WiredRoomDiagnostics;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** AIR's monitor error list: 1132 asks, 1632 clears, 15 answers with Habbo's ten rows. */
class WiredErrorLogsWireTest {

    private static final List<String> OFFICIAL_NAMES = List.of(
            "EXECUTION_CAP",
            "DELAYED_EVENTS_CAP",
            "EXECUTOR_OVERLOAD",
            "MARKED_AS_HEAVY",
            "KILLED",
            "RECURSION_TIMEOUT",
            "TOO_MANY_VARIABLES",
            "TRANSACTION_FAILURE",
            "PLACEMENT_FAILURE",
            "VARIABLE_FX_LIMIT");

    @Test
    void theHeadersAreAirs() {
        assertEquals(1132, Incoming.WiredErrorLogsRequestEvent);
        assertEquals(1632, Incoming.WiredErrorLogsClearEvent);
        assertEquals(15, Outgoing.WiredErrorLogsComposer);
    }

    @Test
    void withoutDiagnosticsHabbosTenRowsComeAtZeroThenOurs() {
        List<WiredErrorLogs.Row> rows = WiredErrorLogs.rows(null, 5_000L);

        assertEquals(13, rows.size());
        for (int index = 0; index < OFFICIAL_NAMES.size(); index++) {
            WiredErrorLogs.Row row = rows.get(index);

            assertEquals(OFFICIAL_NAMES.get(index), row.name());
            assertEquals(Math.min(index, 8), row.errorId(), "placement failure and the fx limit share id 8");
            assertEquals(index == 3 ? "WARNING" : "ERROR", row.category());
            assertEquals(0, row.count());
            assertEquals(-1L, row.msSinceLatest());
        }
        assertEquals(new WiredErrorLogs.Row(100, "NO_TARGETS", "WARNING", 0, -1L), rows.get(10));
        assertEquals(new WiredErrorLogs.Row(101, "UNREACHABLE", "WARNING", 0, -1L), rows.get(11));
        assertEquals("WIRED_LOG", rows.get(12).name());
        assertEquals(102, rows.get(12).errorId());
    }

    @Test
    void theDiagnosticsFillTheirRowsWithTheCountAndTheMillisecondsSinceTheLatest() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1000, 100, 50, 20, 40, 80, 3);
        diagnostics.recordKilled(1_000L, "loop", "box", 7);
        diagnostics.recordKilled(1_400L, "loop", "box", 7);
        diagnostics.recordRecursionTimeout(1_200L, "depth", "signal", 8);
        diagnostics.recordPlacementFailure(1_300L, "room full", "place_furni", 9);
        diagnostics.recordNoTargets(1_500L, "nothing picked", "move", 10);

        List<WiredErrorLogs.Row> rows = WiredErrorLogs.rows(diagnostics.snapshot(0, 10, 0L, 2_000L), 2_000L);

        assertEquals(new WiredErrorLogs.Row(4, "KILLED", "ERROR", 2, 600L), rows.get(4));
        assertEquals(new WiredErrorLogs.Row(5, "RECURSION_TIMEOUT", "ERROR", 1, 800L), rows.get(5));
        assertEquals(new WiredErrorLogs.Row(8, "PLACEMENT_FAILURE", "ERROR", 1, 700L), rows.get(8));
        assertEquals(new WiredErrorLogs.Row(8, "VARIABLE_FX_LIMIT", "ERROR", 0, -1L), rows.get(9));
        assertEquals(new WiredErrorLogs.Row(100, "NO_TARGETS", "WARNING", 1, 500L), rows.get(10));
        assertEquals(0, rows.get(0).count());
    }

    @Test
    void tooManyVariablesTransactionFailuresAndTheFxLimitFillHabbosRows() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1000, 100, 50, 20, 40, 80, 3);
        diagnostics.recordTooManyVariables(1_000L, "holder full", "points", 11);
        diagnostics.recordTooManyVariables(1_100L, "holder full", "points", 11);
        diagnostics.recordTransactionFailure(1_200L, "no usable chest", "init_transaction", 12);
        diagnostics.recordVariableFxLimit(1_900L, "26 fx boxes", "variable_fx", 13);

        WiredRoomDiagnostics.Snapshot snapshot = diagnostics.snapshot(0, 10, 0L, 2_000L);
        List<WiredErrorLogs.Row> rows = WiredErrorLogs.rows(snapshot, 2_000L);

        assertEquals(new WiredErrorLogs.Row(6, "TOO_MANY_VARIABLES", "ERROR", 2, 900L), rows.get(6));
        assertEquals(new WiredErrorLogs.Row(7, "TRANSACTION_FAILURE", "ERROR", 1, 800L), rows.get(7));
        assertEquals(new WiredErrorLogs.Row(8, "PLACEMENT_FAILURE", "ERROR", 0, -1L), rows.get(8));
        assertEquals(new WiredErrorLogs.Row(8, "VARIABLE_FX_LIMIT", "ERROR", 1, 100L), rows.get(9));
        assertEquals(13, rows.size(), "the new types are Habbo's rows, not rows of our own");

        // The info window finds the detail under the row's own name in the monitor packet (5101).
        for (String type : List.of("TOO_MANY_VARIABLES", "TRANSACTION_FAILURE", "VARIABLE_FX_LIMIT")) {
            WiredRoomDiagnostics.LogEntry entry = snapshot.getLogs().stream()
                    .filter(log -> log.getType().name().equals(type))
                    .findFirst()
                    .orElseThrow();
            assertEquals(WiredRoomDiagnostics.Severity.ERROR, entry.getSeverity(), type);
            assertEquals(
                    type.equals("TOO_MANY_VARIABLES") ? 11 : type.equals("TRANSACTION_FAILURE") ? 12 : 13,
                    entry.getLatestSourceId());
        }
    }

    @Test
    void clearingPutsEveryRowBackAtZero() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1000, 100, 50, 20, 40, 80, 3);
        diagnostics.recordKilled(1_000L, "loop", "box", 7);
        diagnostics.clearLogs();

        for (WiredErrorLogs.Row row : WiredErrorLogs.rows(diagnostics.snapshot(0, 10, 0L, 2_000L), 2_000L)) {
            assertEquals(0, row.count(), row.name());
            assertEquals(-1L, row.msSinceLatest(), row.name());
        }
    }

    @Test
    void thePacketFollowsAirsLayout() {
        ByteBuffer body = body(
                new WiredErrorLogsComposer(List.of(
                        new WiredErrorLogs.Row(3, "MARKED_AS_HEAVY", "WARNING", 4, 0x0102030405L),
                        new WiredErrorLogs.Row(6, "TOO_MANY_VARIABLES", "ERROR", 0, -1L))),
                Outgoing.WiredErrorLogsComposer);

        assertEquals(2, body.getInt());
        assertEquals(3, body.getInt());
        assertEquals("MARKED_AS_HEAVY", string(body));
        assertEquals("WARNING", string(body));
        assertEquals(4, body.getInt());
        assertEquals(0x0102030405L, body.getLong());
        assertEquals(6, body.getInt());
        assertEquals("TOO_MANY_VARIABLES", string(body));
        assertEquals("ERROR", string(body));
        assertEquals(0, body.getInt());
        assertEquals(-1L, body.getLong());
        assertEquals(0, body.remaining());
    }

    @Test
    void theMonitorPacketKeepsOnlyTheLatestDetailOfEachType() {
        WiredRoomDiagnostics diagnostics = new WiredRoomDiagnostics(1000, 100, 50, 20, 40, 80, 3);
        diagnostics.recordKilled(1_000L, "loop", "box", 7);
        WiredRoomDiagnostics.Snapshot snapshot = diagnostics.snapshot(0, 10, 0L, 2_000L);

        ByteBuffer body = body(new WiredMonitorDataComposer(snapshot), Outgoing.WiredMonitorDataComposer);

        // 17 counters and thresholds (the heavy flag is the third), then the log rows.
        body.position(16 * 4 + 1);
        int logs = body.getInt();
        assertEquals(snapshot.getLogs().size(), logs);
        for (WiredRoomDiagnostics.LogEntry entry : snapshot.getLogs()) {
            assertEquals(entry.getType().name(), string(body));
            assertEquals(Objects.toString(entry.getLatestReason(), ""), string(body));
            assertEquals(Objects.toString(entry.getLatestSourceLabel(), ""), string(body));
            body.getInt();
        }
        assertEquals(snapshot.getHistory().size(), body.getInt());
    }

    private static String string(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.getShort()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** The packet body after the frame length and the header, which must be {@code header}. */
    private static ByteBuffer body(MessageComposer composer, int header) {
        ByteBuf packet = composer.compose().get();
        try {
            byte[] bytes = new byte[packet.readableBytes()];
            packet.getBytes(packet.readerIndex(), bytes);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            assertEquals(bytes.length - 4, buffer.getInt());
            assertEquals(header, buffer.getShort());
            return buffer.slice();
        } finally {
            packet.release();
        }
    }
}
