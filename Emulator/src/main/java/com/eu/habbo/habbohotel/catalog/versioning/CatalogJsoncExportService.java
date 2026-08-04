package com.eu.habbo.habbohotel.catalog.versioning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Objects;

public final class CatalogJsoncExportService {
    private final Gson gson =
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String export(CatalogVersionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return "{\n"
                + "  // Catalog Studio JSONC export. Comments may be kept when importing.\n"
                + "  \"schemaVersion\": " + CatalogJsoncDocument.CURRENT_SCHEMA_VERSION + ",\n"
                + "  \"baseVersionId\": " + snapshot.version().id() + ",\n"
                + "  \"baseRevision\": " + snapshot.version().revision() + ",\n"
                + "  // Pages use stable IDs. NORMAL and BUILDER IDs are independent.\n"
                + "  \"pages\": " + indent(gson.toJson(snapshot.pages())) + ",\n"
                + "  // limited_sells is operational state and is intentionally never exported.\n"
                + "  \"offers\": " + indent(gson.toJson(snapshot.offers())) + "\n"
                + "}\n";
    }

    private static String indent(String json) {
        return json.replace("\n", "\n  ");
    }
}
