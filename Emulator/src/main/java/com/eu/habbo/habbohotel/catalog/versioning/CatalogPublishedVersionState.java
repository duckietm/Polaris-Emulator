package com.eu.habbo.habbohotel.catalog.versioning;

import java.time.Instant;
import java.util.Objects;

public record CatalogPublishedVersionState(long id, String label, Instant publishedAt) {
    public CatalogPublishedVersionState {
        label = Objects.requireNonNull(label, "label");
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
    }
}
