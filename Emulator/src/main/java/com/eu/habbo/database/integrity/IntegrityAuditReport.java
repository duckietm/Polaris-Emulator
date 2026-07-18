package com.eu.habbo.database.integrity;

import java.util.List;

public record IntegrityAuditReport(
        int contractVersion,
        int relationChecks,
        int duplicateChecks,
        long elapsedMillis,
        List<IntegrityFinding> findings,
        List<IntegrityAuditError> errors) {

    public IntegrityAuditReport {
        findings = List.copyOf(findings);
        errors = List.copyOf(errors);
    }

    public boolean isHealthy() {
        return findings.isEmpty() && errors.isEmpty();
    }

    public long affectedRows() {
        return findings.stream().mapToLong(IntegrityFinding::affectedRows).sum();
    }
}
