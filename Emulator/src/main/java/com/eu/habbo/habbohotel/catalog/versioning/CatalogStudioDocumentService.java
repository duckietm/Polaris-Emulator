package com.eu.habbo.habbohotel.catalog.versioning;

import com.google.gson.Gson;
import java.util.Locale;
import java.util.Objects;

public final class CatalogStudioDocumentService {
    private final Gson gson;

    public CatalogStudioDocumentService(Gson gson) {
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public CatalogImportDryRun dryRun(CatalogVersionSnapshot snapshot, String format, String document) {
        return switch (Objects.requireNonNull(format, "format").trim().toUpperCase(Locale.ROOT)) {
            case "JSONC" -> new CatalogJsoncImportService().dryRun(snapshot, document);
            case "SQL" -> new CatalogSqlImportService().dryRun(snapshot, document);
            case "BULK" -> bulk(snapshot, document);
            default -> throw new IllegalArgumentException("Unsupported Catalog Studio document format: " + format);
        };
    }

    public String export(CatalogVersionSnapshot snapshot, String format) {
        return switch (Objects.requireNonNull(format, "format").trim().toUpperCase(Locale.ROOT)) {
            case "JSONC" -> new CatalogJsoncExportService().export(snapshot);
            case "SQL" -> new CatalogSqlExportService().export(snapshot);
            default -> throw new IllegalArgumentException("Unsupported Catalog Studio export format: " + format);
        };
    }

    private CatalogImportDryRun bulk(CatalogVersionSnapshot snapshot, String document) {
        CatalogBulkRequest request = gson.fromJson(document, CatalogBulkRequest.class);
        if (request == null) throw new IllegalArgumentException("Bulk operation is empty");
        CatalogBulkDryRun dryRun = new CatalogBulkOperationService(gson).dryRun(snapshot, request);
        return new CatalogImportDryRun(
                CatalogChangeSource.UI,
                dryRun.draftVersionId(),
                dryRun.revision(),
                gson.toJson(request),
                dryRun.changes(),
                dryRun.fingerprint());
    }
}
