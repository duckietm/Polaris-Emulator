package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Locale;
import java.util.Objects;

public final class CatalogStudioDocumentService {
    public CatalogImportDryRun dryRun(CatalogVersionSnapshot snapshot, String format, String document) {
        return switch (Objects.requireNonNull(format, "format").trim().toUpperCase(Locale.ROOT)) {
            case "SQL" -> new CatalogSqlImportService().dryRun(snapshot, document);
            default -> throw new IllegalArgumentException("Unsupported Catalog Studio document format: " + format);
        };
    }

    public String export(CatalogVersionSnapshot snapshot, String format) {
        return switch (Objects.requireNonNull(format, "format").trim().toUpperCase(Locale.ROOT)) {
            case "SQL" -> new CatalogSqlExportService().export(snapshot);
            default -> throw new IllegalArgumentException("Unsupported Catalog Studio export format: " + format);
        };
    }
}
