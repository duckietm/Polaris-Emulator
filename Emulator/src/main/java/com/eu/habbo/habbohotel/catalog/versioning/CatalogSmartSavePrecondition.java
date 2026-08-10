package com.eu.habbo.habbohotel.catalog.versioning;

@FunctionalInterface
public interface CatalogSmartSavePrecondition {
    void validate(CatalogSmartSaveDraft draft);
}
