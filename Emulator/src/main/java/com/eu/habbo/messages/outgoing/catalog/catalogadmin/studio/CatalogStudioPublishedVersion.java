package com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio;

import java.time.Instant;
import java.util.Objects;

public record CatalogStudioPublishedVersion(long id, String label, Instant publishedAt) {
    public CatalogStudioPublishedVersion {
        label = Objects.requireNonNull(label, "label");
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
    }
}
