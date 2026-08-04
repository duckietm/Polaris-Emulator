package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;

public record CatalogDraftMutationBatchResult(long revision, List<CatalogChangeEntry> changes) {
    public CatalogDraftMutationBatchResult {
        changes = List.copyOf(changes);
    }
}
