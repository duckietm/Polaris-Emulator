package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;

public record CatalogValidationReport(List<CatalogValidationIssue> issues) {
    public CatalogValidationReport {
        issues = List.copyOf(issues);
    }

    public boolean valid() {
        return issues.isEmpty();
    }
}
