package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record CatalogSqlStatement(CatalogSqlAction action, String table, Map<String, String> values, Integer whereId) {
    public CatalogSqlStatement {
        action = Objects.requireNonNull(action, "action");
        table = Objects.requireNonNull(table, "table");
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        if ((action == CatalogSqlAction.UPDATE || action == CatalogSqlAction.DELETE)
                && (whereId == null || whereId <= 0)) {
            throw new IllegalArgumentException("UPDATE and DELETE require one positive ID");
        }
        if (action == CatalogSqlAction.DELETE_ALL && whereId != null) {
            throw new IllegalArgumentException("DELETE_ALL cannot target one ID");
        }
    }
}
