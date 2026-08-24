package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Objects;

public final class CatalogLiveValidationException extends IllegalArgumentException {
    private final CatalogValidationReport report;

    public CatalogLiveValidationException(CatalogValidationReport report) {
        super(message(report));
        this.report = Objects.requireNonNull(report, "report");
    }

    public CatalogValidationReport report() {
        return report;
    }

    private static String message(CatalogValidationReport report) {
        Objects.requireNonNull(report, "report");
        if (report.valid()) return "Catalog change is valid";
        CatalogValidationIssue first = report.issues().getFirst();
        return "Change introduces " + report.issues().size() + " catalog problem(s): " + first.message();
    }
}
