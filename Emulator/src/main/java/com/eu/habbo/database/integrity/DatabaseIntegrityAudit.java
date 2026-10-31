package com.eu.habbo.database.integrity;

import com.eu.habbo.core.ConfigurationManager;
import com.google.gson.Gson;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class DatabaseIntegrityAudit {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseIntegrityAudit.class);
    private static final Gson GSON = new Gson();
    private static final Path REPORT_PATH = Path.of("logging", "database-integrity-audit.json");
    private static final int MAX_LOGGED_SAMPLES = 3;
    /** Room above the audit's queryTimeout, so the server's own timeout fires before the socket gives up. */
    static final int AUDIT_SOCKET_MARGIN_SECONDS = 30;

    private DatabaseIntegrityAudit() {
    }

    public static IntegrityAuditReport auditAtStartup(
            DataSource dataSource,
            ConfigurationManager config,
            IntegrityAuditOptions options) {
        Objects.requireNonNull(dataSource, "dataSource");
        IntegrityAuditSettings settings = IntegrityAuditSettings.resolve(config, options);
        if (settings.mode() == IntegrityAuditMode.OFF) {
            LOGGER.warn("Database integrity audit -> disabled by configuration");
            return new IntegrityAuditReport(1, 0, 0, 0, List.of(), List.of());
        }

        IntegrityAuditReport report;
        // The audit holds one connection for its whole run: the pool's leak detection would report it as a leak.
        HikariDataSource pool = dataSource instanceof HikariDataSource hikari ? hikari : null;
        long leakThreshold = pool != null ? pool.getLeakDetectionThreshold() : 0;
        if (pool != null && leakThreshold > 0) {
            pool.setLeakDetectionThreshold(0);
        }
        try (Connection connection = dataSource.getConnection()) {
            // The pool's socketTimeout (30 s) is sized for gameplay queries, while an audit query may run for its
            // whole queryTimeout (up to 300 s): the socket closed under it and every later check failed. The pool
            // restores the connection's own timeout when it is returned.
            connection.setNetworkTimeout(Runnable::run, (settings.queryTimeoutSeconds() + AUDIT_SOCKET_MARGIN_SECONDS) * 1000);
            IntegrityContract contract = IntegrityContractLoader.load(
                    DatabaseIntegrityAudit.class.getClassLoader());
            report = new DatabaseIntegrityAuditor(
                    connection,
                    contract,
                    settings.sampleLimit(),
                    settings.queryTimeoutSeconds(),
                    settings.maxDurationSeconds()).audit();
        } catch (RuntimeException error) {
            if (settings.mode() == IntegrityAuditMode.STRICT) {
                throw new IntegrityAuditException(
                        "Database integrity audit could not run in strict mode", error);
            }
            LOGGER.warn("Database integrity audit could not run; startup continues in WARN mode", error);
            return new IntegrityAuditReport(
                    1, 0, 0, 0, List.of(),
                    List.of(new IntegrityAuditError(
                            "audit-startup", bounded(error))));
        } catch (Exception error) {
            if (settings.mode() == IntegrityAuditMode.STRICT) {
                throw new IntegrityAuditException(
                        "Database integrity audit could not run in strict mode", error);
            }
            LOGGER.warn("Database integrity audit could not run; startup continues in WARN mode", error);
            return new IntegrityAuditReport(
                    1, 0, 0, 0, List.of(),
                    List.of(new IntegrityAuditError(
                            "audit-startup", bounded(error))));
        } finally {
            if (pool != null && leakThreshold > 0) {
                pool.setLeakDetectionThreshold(leakThreshold);
            }
        }

        log(report, settings.mode());
        enforce(report, settings.mode());
        return report;
    }

    static void enforce(IntegrityAuditReport report, IntegrityAuditMode mode) {
        if (mode == IntegrityAuditMode.STRICT && !report.isHealthy()) {
            throw new IntegrityAuditException(
                    "Database integrity audit failed: findings=" + report.findings().size()
                            + ", errors=" + report.errors().size()
                            + ", affectedRows=" + report.affectedRows());
        }
    }

    private static void log(IntegrityAuditReport report, IntegrityAuditMode mode) {
        if (report.isHealthy()) {
            clearReport(REPORT_PATH);
            LOGGER.info(
                    "Database integrity audit -> clean, relations={}, duplicates={}, durationMs={}, mode={}",
                    report.relationChecks(), report.duplicateChecks(), report.elapsedMillis(), mode);
            return;
        }
        String reportNote = writeReport(report, REPORT_PATH) ? "; full report: " + REPORT_PATH : "";
        int total = report.findings().size();
        LOGGER.warn(
                "Database integrity audit -> {} finding(s) affecting {} row(s) ({} ms, mode {}){}",
                total, report.affectedRows(), report.elapsedMillis(), mode, reportNote);
        int index = 0;
        for (IntegrityFinding finding : report.findings()) {
            LOGGER.warn("{}", findingLine(++index, total, finding));
            LOGGER.debug(
                    "Database integrity finding detail -> check={}, type={}, source={}, affectedRows={}, groups={}, samples={}",
                    finding.checkId(), finding.type(), finding.source(), finding.affectedRows(),
                    finding.groups(), finding.samples());
        }
        report.errors().forEach(error -> LOGGER.warn(
                "  audit error {}: {}", error.checkId(), error.message()));
    }

    static String findingLine(int index, int total, IntegrityFinding finding) {
        StringBuilder line = new StringBuilder()
                .append("  [").append(index).append('/').append(total).append("] ")
                .append(finding.checkId())
                .append(" (").append(finding.type().name().toLowerCase(Locale.ROOT)).append("): ")
                .append(finding.affectedRows()).append(" rows - ")
                .append(finding.description());
        String samples = compactSamples(finding.samples());
        if (!samples.isEmpty()) line.append("; e.g. ").append(samples);
        return line.toString();
    }

    static String compactSamples(List<IntegritySample> samples) {
        Map<String, Long> distinct = new LinkedHashMap<>();
        for (IntegritySample sample : samples) {
            StringBuilder values = new StringBuilder();
            sample.values().forEach((column, value) -> {
                if (!values.isEmpty()) values.append(' ');
                values.append(singleLine(column))
                        .append('=')
                        .append(singleLine(value));
            });
            distinct.merge(values.toString(), sample.occurrences(), Long::max);
        }
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Long> entry : distinct.entrySet()) {
            if (shown == MAX_LOGGED_SAMPLES) {
                out.append("; ...");
                break;
            }
            if (shown > 0) out.append("; ");
            out.append(entry.getKey());
            if (entry.getValue() > 1) out.append(" x").append(entry.getValue());
            shown++;
        }
        return out.toString();
    }

    private static String singleLine(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    static boolean writeReport(IntegrityAuditReport report, Path path) {
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("generatedAt", Instant.now().toString());
            document.put("contractVersion", report.contractVersion());
            document.put("durationMs", report.elapsedMillis());
            document.put("affectedRows", report.affectedRows());
            List<Map<String, Object>> findings = new ArrayList<>();
            for (IntegrityFinding finding : report.findings()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("check", finding.checkId());
                entry.put("type", finding.type().name());
                entry.put("source", finding.source().name());
                entry.put("affectedRows", finding.affectedRows());
                entry.put("groups", finding.groups());
                entry.put("description", finding.description());
                List<Map<String, Object>> sampleEntries = new ArrayList<>();
                for (IntegritySample sample : finding.samples()) {
                    Map<String, Object> sampleEntry = new LinkedHashMap<>();
                    sampleEntry.put("values", sample.values());
                    sampleEntry.put("occurrences", sample.occurrences());
                    sampleEntries.add(sampleEntry);
                }
                entry.put("samples", sampleEntries);
                findings.add(entry);
            }
            document.put("findings", findings);
            List<Map<String, String>> errors = new ArrayList<>();
            for (IntegrityAuditError error : report.errors()) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("check", error.checkId());
                entry.put("error", error.message());
                errors.add(entry);
            }
            document.put("errors", errors);
            Files.writeString(path, GSON.toJson(document) + System.lineSeparator(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException | RuntimeException error) {
            LOGGER.debug("Unable to write the database integrity report", error);
            return false;
        }
    }

    static void clearReport(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException error) {
            LOGGER.debug("Unable to remove the previous database integrity report", error);
        }
    }

    private static String bounded(Throwable error) {
        String value = error.getClass().getSimpleName() + ": " + error.getMessage();
        value = value.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() <= 300 ? value : value.substring(0, 297) + "...";
    }
}
