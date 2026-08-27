package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Objects;

/**
 * The live catalog plus its validation result, read in one pass.
 *
 * <p>Opening a Manager session needs both. Asking for them separately reads and locks the whole catalog twice, which on
 * a large catalog is seconds of work on the thread handling the operator's packet.
 */
public record CatalogLiveSession(CatalogVersionSnapshot live, CatalogDraftValidationResult validation) {
    public CatalogLiveSession {
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(validation, "validation");
    }
}
