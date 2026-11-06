package polaris.hotel.catalog.versioning;

import java.util.Objects;

public record CatalogDraftValidationResult(long revision, CatalogValidationReport report) {
    public CatalogDraftValidationResult {
        report = Objects.requireNonNull(report, "report");
    }
}
