package com.eu.habbo.database.observability;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlowQueryMonitorTest {

    @Test
    void emitsStructuredContextOnlyWhenTheThresholdIsReached() throws Exception {
        List<SlowQueryEvent> events = new ArrayList<>();
        SlowQueryMonitor monitor = monitor(events, clock(0, 300_000_000L), 250);
        Connection connection = monitor.wrap(connectionReturning(statementReturning(false)));

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM users WHERE username = 'alice' AND id = 42")) {
            statement.executeQuery();
        }

        assertEquals(1, events.size());
        SlowQueryEvent event = events.getFirst();
        assertEquals(300, event.durationMs());
        assertEquals("executeQuery", event.operation());
        assertTrue(event.success());
        assertEquals("SELECT * FROM users WHERE username = ? AND id = ?", event.sql());
        assertEquals(16, event.fingerprint().length());
        assertEquals("test-worker", event.threadName());
        assertEquals(new PoolSnapshot(3, 7, 10, 1), event.pool());
    }

    @Test
    void ignoresFastQueries() throws Exception {
        List<SlowQueryEvent> events = new ArrayList<>();
        SlowQueryMonitor monitor = monitor(events, clock(0, 249_999_999L), 250);
        Connection connection = monitor.wrap(connectionReturning(statementReturning(false)));

        connection.createStatement().execute("SELECT 1");

        assertTrue(events.isEmpty());
    }

    @Test
    void recordsSafeFailureMetadataWithoutLoggingTheDriverMessage() throws Exception {
        List<SlowQueryEvent> events = new ArrayList<>();
        SlowQueryMonitor monitor = monitor(events, clock(0, 400_000_000L), 250);
        Connection connection = monitor.wrap(connectionReturning(statementReturning(true)));

        SQLException error = assertThrows(SQLException.class,
                () -> connection.createStatement().executeUpdate(
                        "UPDATE users SET auth_ticket = 'secret-ticket' WHERE id = 5"));

        assertEquals("private driver message", error.getMessage());
        SlowQueryEvent event = events.getFirst();
        assertFalse(event.success());
        assertEquals("42000", event.sqlState());
        assertEquals(1064, event.vendorCode());
        assertFalse(event.sql().contains("secret-ticket"));
        assertFalse(event.toString().contains("private driver message"));
    }

    @Test
    void loggingFailureNeverChangesTheQueryResult() throws Exception {
        SlowQueryMonitor monitor = new SlowQueryMonitor(
                new SlowQuerySettings(true, 1, 512),
                event -> { throw new IllegalStateException("sink unavailable"); },
                clock(0, 2_000_000L),
                () -> PoolSnapshot.UNAVAILABLE,
                () -> "test-worker");
        Connection connection = monitor.wrap(connectionReturning(statementReturning(false)));

        assertDoesNotThrow(() -> connection.createStatement().execute("SELECT 1"));
    }

    @Test
    void summarizesStatementBatchesWithoutLoggingEveryValue() throws Exception {
        List<SlowQueryEvent> events = new ArrayList<>();
        SlowQueryMonitor monitor = monitor(events, clock(0, 300_000_000L), 250);
        Connection connection = monitor.wrap(connectionReturning(statementReturning(false)));
        Statement statement = connection.createStatement();

        statement.addBatch("UPDATE users SET credits = 100 WHERE id = 1");
        statement.addBatch("UPDATE users SET credits = 200 WHERE id = 2");
        statement.executeBatch();

        SlowQueryEvent event = events.getFirst();
        assertEquals("executeBatch", event.operation());
        assertTrue(event.sql().startsWith("BATCH size=? first=UPDATE users SET credits = ? WHERE id = ?"));
        assertFalse(event.sql().contains("200"));
    }

    private static SlowQueryMonitor monitor(
            List<SlowQueryEvent> events,
            LongSupplier clock,
            long thresholdMs) {
        return new SlowQueryMonitor(
                new SlowQuerySettings(true, thresholdMs, 512),
                events::add,
                clock,
                () -> new PoolSnapshot(3, 7, 10, 1),
                () -> "test-worker");
    }

    private static LongSupplier clock(long... values) {
        AtomicInteger index = new AtomicInteger();
        return () -> values[Math.min(index.getAndIncrement(), values.length - 1)];
    }

    private static Connection connectionReturning(Statement statement) {
        return (Connection) Proxy.newProxyInstance(
                SlowQueryMonitorTest.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement", "prepareCall", "createStatement" -> statement;
                    case "close" -> null;
                    case "isClosed" -> false;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static PreparedStatement statementReturning(boolean fail) {
        return (PreparedStatement) Proxy.newProxyInstance(
                SlowQueryMonitorTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("execute")) {
                        if (fail) {
                            throw new SQLException("private driver message", "42000", 1064);
                        }
                        return defaultValue(method.getReturnType());
                    }
                    if ("close".equals(method.getName())) return null;
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
