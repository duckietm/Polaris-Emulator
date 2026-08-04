package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.List;

public record CatalogHistoryPage(
        long draftVersionId, long revision, int totalCount, List<CatalogHistoryGroupState> groups) {
    public CatalogHistoryPage {
        groups = List.copyOf(groups);
    }
}
