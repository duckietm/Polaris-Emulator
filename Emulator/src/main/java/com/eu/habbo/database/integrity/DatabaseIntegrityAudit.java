package com.eu.habbo.database.integrity;

import com.eu.habbo.core.ConfigurationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;

public final class DatabaseIntegrityAudit {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseIntegrityAudit.class);

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
        try (Connection connection = dataSource.getConnection()) {
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
            LOGGER.info(
                    "Database integrity audit -> clean, relations={}, duplicates={}, durationMs={}, mode={}",
                    report.relationChecks(), report.duplicateChecks(), report.elapsedMillis(), mode);
            return;
        }
        LOGGER.warn(
                "Database integrity audit -> findings={}, errors={}, affectedRows={}, durationMs={}, mode={}",
                report.findings().size(), report.errors().size(), report.affectedRows(),
                report.elapsedMillis(), mode);
        report.findings().forEach(finding -> LOGGER.warn(
                "Database integrity finding -> check={}, type={}, source={}, affectedRows={}, groups={}, samples={}, description={}",
                finding.checkId(), finding.type(), finding.source(), finding.affectedRows(),
                finding.groups(), finding.samples(), finding.description()));
        report.errors().forEach(error -> LOGGER.warn(
                "Database integrity error -> check={}, error={}",
                error.checkId(), error.message()));
    }

    private static String bounded(Throwable error) {
        String value = error.getClass().getSimpleName() + ": " + error.getMessage();
        value = value.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() <= 300 ? value : value.substring(0, 297) + "...";
    }
}
