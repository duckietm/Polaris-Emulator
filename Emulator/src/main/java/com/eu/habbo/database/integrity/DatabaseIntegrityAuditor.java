package com.eu.habbo.database.integrity;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DatabaseIntegrityAuditor {
    private final Connection connection;
    private final IntegrityContract contract;
    private final int sampleLimit;
    private final int queryTimeoutSeconds;
    private final int maxDurationSeconds;

    public DatabaseIntegrityAuditor(
            Connection connection,
            IntegrityContract contract,
            int sampleLimit,
            int queryTimeoutSeconds) {
        this(connection, contract, sampleLimit, queryTimeoutSeconds, 120);
    }

    public DatabaseIntegrityAuditor(
            Connection connection,
            IntegrityContract contract,
            int sampleLimit,
            int queryTimeoutSeconds,
            int maxDurationSeconds) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.contract = Objects.requireNonNull(contract, "contract");
        if (sampleLimit < 0 || sampleLimit > 100) {
            throw new IllegalArgumentException("sampleLimit must be between 0 and 100");
        }
        if (queryTimeoutSeconds < 1 || queryTimeoutSeconds > 300) {
            throw new IllegalArgumentException("queryTimeoutSeconds must be between 1 and 300");
        }
        if (maxDurationSeconds < 1 || maxDurationSeconds > 1800) {
            throw new IllegalArgumentException("maxDurationSeconds must be between 1 and 1800");
        }
        this.sampleLimit = sampleLimit;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.maxDurationSeconds = maxDurationSeconds;
    }

    public IntegrityAuditReport audit() {
        long started = System.nanoTime();
        long deadline = started + maxDurationSeconds * 1_000_000_000L;
        List<IntegrityFinding> findings = new ArrayList<>();
        List<IntegrityAuditError> errors = new ArrayList<>();
        List<RelationRequirement> relations = new ArrayList<>(contract.logicalRelations());
        try {
            Set<String> signatures = new HashSet<>();
            relations.forEach(relation -> signatures.add(relation.signature()));
            for (RelationRequirement relation : ForeignKeyDiscovery.discover(
                    connection, queryTimeoutSeconds)) {
                if (signatures.add(relation.signature())) relations.add(relation);
            }
        } catch (SQLException | RuntimeException error) {
            errors.add(error("foreign-key-discovery", error));
        }

        for (RelationRequirement relation : relations) {
            if (deadlineExceeded(deadline, errors)) break;
            try {
                long count = singleLong(IntegritySql.relationCount(relation));
                if (count > 0) {
                    findings.add(new IntegrityFinding(
                            relation.id(),
                            IntegrityFindingType.ORPHAN,
                            relation.source(),
                            count,
                            count,
                            sampleLimit == 0 ? List.of() : relationSamples(relation),
                            relation.description()));
                }
            } catch (SQLException | RuntimeException error) {
                errors.add(error(relation.id(), error));
            }
        }

        for (DuplicateRequirement duplicate : contract.duplicateKeys()) {
            if (deadlineExceeded(deadline, errors)) break;
            try {
                long[] totals = duplicateTotals(duplicate);
                if (totals[1] > 0) {
                    findings.add(new IntegrityFinding(
                            duplicate.id(),
                            IntegrityFindingType.DUPLICATE,
                            IntegrityCheckSource.LOGICAL_CONTRACT,
                            totals[1],
                            totals[0],
                            sampleLimit == 0 ? List.of() : duplicateSamples(duplicate),
                            duplicate.description()));
                }
            } catch (SQLException | RuntimeException error) {
                errors.add(error(duplicate.id(), error));
            }
        }

        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        return new IntegrityAuditReport(
                contract.schemaVersion(),
                relations.size(),
                contract.duplicateKeys().size(),
                elapsedMillis,
                findings,
                errors);
    }

    private long singleLong(String sql) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Integrity count returned no row");
                return rows.getLong(1);
            }
        }
    }

    private long[] duplicateTotals(DuplicateRequirement duplicate) throws SQLException {
        try (var statement = connection.prepareStatement(IntegritySql.duplicateCount(duplicate))) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Duplicate count returned no row");
                return new long[]{rows.getLong("duplicate_groups"), rows.getLong("affected_rows")};
            }
        }
    }

    private List<IntegritySample> relationSamples(RelationRequirement relation) throws SQLException {
        List<IntegritySample> samples = new ArrayList<>();
        try (var statement = connection.prepareStatement(
                IntegritySql.relationSamples(relation, sampleLimit))) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Map<String, String> values = new LinkedHashMap<>();
                    for (String column : relation.childColumns()) {
                        values.put(column, display(rows.getObject(column)));
                    }
                    samples.add(new IntegritySample(values, 1));
                }
            }
        }
        return samples;
    }

    private List<IntegritySample> duplicateSamples(DuplicateRequirement duplicate)
            throws SQLException {
        List<IntegritySample> samples = new ArrayList<>();
        try (var statement = connection.prepareStatement(
                IntegritySql.duplicateSamples(duplicate, sampleLimit))) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    Map<String, String> values = new LinkedHashMap<>();
                    for (String column : duplicate.columns()) {
                        values.put(column, display(rows.getObject(column)));
                    }
                    samples.add(new IntegritySample(
                            values, rows.getLong("duplicate_count")));
                }
            }
        }
        return samples;
    }

    private static String display(Object value) {
        if (value == null) return "<null>";
        String text = String.valueOf(value);
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
    }

    private static IntegrityAuditError error(String checkId, Throwable error) {
        String message;
        if (error instanceof SQLException sql) {
            message = "SQL state=" + String.valueOf(sql.getSQLState())
                    + " code=" + sql.getErrorCode() + ": " + sql.getMessage();
        } else {
            message = error.getClass().getSimpleName() + ": " + error.getMessage();
        }
        if (message == null) message = error.getClass().getName();
        message = message.replace('\r', ' ').replace('\n', ' ').trim();
        if (message.length() > 300) message = message.substring(0, 297) + "...";
        return new IntegrityAuditError(checkId, message);
    }

    private static boolean deadlineExceeded(
            long deadline,
            List<IntegrityAuditError> errors) {
        if (System.nanoTime() <= deadline) return false;
        if (errors.stream().noneMatch(error -> error.checkId().equals("audit-deadline"))) {
            errors.add(new IntegrityAuditError(
                    "audit-deadline",
                    "The global audit duration limit was reached before every check completed"));
        }
        return true;
    }
}
