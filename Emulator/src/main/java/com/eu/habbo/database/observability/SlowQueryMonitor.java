package com.eu.habbo.database.observability;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class SlowQueryMonitor {

    private static final Set<String> EXECUTION_METHODS = Set.of(
            "execute", "executeQuery", "executeUpdate", "executeLargeUpdate",
            "executeBatch", "executeLargeBatch");

    private final SlowQuerySettings settings;
    private final SlowQuerySink sink;
    private final LongSupplier nanoTime;
    private final Supplier<PoolSnapshot> poolSnapshot;
    private final Supplier<String> threadName;

    public SlowQueryMonitor(
            SlowQuerySettings settings,
            SlowQuerySink sink,
            LongSupplier nanoTime,
            Supplier<PoolSnapshot> poolSnapshot,
            Supplier<String> threadName) {
        this.settings = settings;
        this.sink = sink;
        this.nanoTime = nanoTime;
        this.poolSnapshot = poolSnapshot;
        this.threadName = threadName;
    }

    public Connection wrap(Connection connection) {
        if (!this.settings.enabled()) {
            return connection;
        }
        return (Connection) Proxy.newProxyInstance(
                SlowQueryMonitor.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionHandler(connection));
    }

    private final class ConnectionHandler implements InvocationHandler {
        private final Connection delegate;

        private ConnectionHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = invokeDelegate(method, this.delegate, args);
            if (!(result instanceof Statement statement)) {
                return result;
            }

            String sql = null;
            if (args != null && args.length > 0 && args[0] instanceof String value
                    && ("prepareStatement".equals(method.getName()) || "prepareCall".equals(method.getName()))) {
                sql = value;
            }
            return wrapStatement(statement, sql, (Connection) proxy);
        }
    }

    private Statement wrapStatement(Statement statement, String preparedSql, Connection owner) {
        Class<?> statementType = Statement.class;
        if (statement instanceof CallableStatement) {
            statementType = CallableStatement.class;
        } else if (statement instanceof PreparedStatement) {
            statementType = PreparedStatement.class;
        }

        return (Statement) Proxy.newProxyInstance(
                SlowQueryMonitor.class.getClassLoader(),
                new Class<?>[]{statementType},
                new StatementHandler(statement, preparedSql, owner));
    }

    private final class StatementHandler implements InvocationHandler {
        private final Statement delegate;
        private final String preparedSql;
        private final Connection owner;
        private final List<String> batchSql = new ArrayList<>();

        private StatementHandler(Statement delegate, String preparedSql, Connection owner) {
            this.delegate = delegate;
            this.preparedSql = preparedSql;
            this.owner = owner;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if ("getConnection".equals(methodName)) {
                return this.owner;
            }
            if ("addBatch".equals(methodName)) {
                if (args != null && args.length > 0 && args[0] instanceof String value) {
                    this.batchSql.add(value);
                } else if (this.preparedSql != null) {
                    this.batchSql.add(this.preparedSql);
                }
                return invokeDelegate(method, this.delegate, args);
            }
            if ("clearBatch".equals(methodName)) {
                this.batchSql.clear();
                return invokeDelegate(method, this.delegate, args);
            }
            if (!EXECUTION_METHODS.contains(methodName)) {
                return invokeDelegate(method, this.delegate, args);
            }

            String sql = resolveSql(args, this.preparedSql, this.batchSql);
            long startedAt = nanoTime.getAsLong();
            Throwable failure = null;
            try {
                return invokeDelegate(method, this.delegate, args);
            } catch (Throwable error) {
                failure = error;
                throw error;
            } finally {
                long elapsedNanos = Math.max(0, nanoTime.getAsLong() - startedAt);
                emitIfSlow(methodName, sql, elapsedNanos, failure);
                if ("executeBatch".equals(methodName) || "executeLargeBatch".equals(methodName)) {
                    this.batchSql.clear();
                }
            }
        }
    }

    private void emitIfSlow(String operation, String sql, long elapsedNanos, Throwable failure) {
        if (elapsedNanos < TimeUnit.MILLISECONDS.toNanos(this.settings.thresholdMs())) {
            return;
        }

        String sanitized = SlowQuerySql.sanitize(sql, this.settings.maxSqlLength());
        SQLException sqlError = findSqlException(failure);
        PoolSnapshot pool = safePoolSnapshot();
        SlowQueryEvent event = new SlowQueryEvent(
                TimeUnit.NANOSECONDS.toMillis(elapsedNanos),
                operation,
                failure == null,
                sqlError == null || sqlError.getSQLState() == null ? "-" : sqlError.getSQLState(),
                sqlError == null ? 0 : sqlError.getErrorCode(),
                SlowQuerySql.fingerprint(sql),
                sanitized,
                safeThreadName(),
                pool);
        try {
            this.sink.accept(event);
        } catch (RuntimeException ignored) {
            // Diagnostics must never alter database behavior.
        }
    }

    private PoolSnapshot safePoolSnapshot() {
        try {
            PoolSnapshot snapshot = this.poolSnapshot.get();
            return snapshot == null ? PoolSnapshot.UNAVAILABLE : snapshot;
        } catch (RuntimeException ignored) {
            return PoolSnapshot.UNAVAILABLE;
        }
    }

    private String safeThreadName() {
        try {
            String name = this.threadName.get();
            return name == null || name.isBlank() ? "unknown" : name;
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private static String resolveSql(Object[] args, String preparedSql, List<String> batchSql) {
        if (args != null && args.length > 0 && args[0] instanceof String value) {
            return value;
        }
        if (!batchSql.isEmpty()) {
            return "BATCH size=" + batchSql.size() + " first=" + batchSql.getFirst();
        }
        return preparedSql;
    }

    private static SQLException findSqlException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    private static Object invokeDelegate(Method method, Object delegate, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException error) {
            throw error.getCause() == null ? error : error.getCause();
        }
    }
}
