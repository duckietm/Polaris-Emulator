package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Map;

public record CatalogPreviewPersona(
        int rank,
        boolean hc,
        boolean vip,
        boolean buildersClub,
        boolean showHidden,
        int credits,
        Map<Integer, Integer> currencies) {
    public CatalogPreviewPersona {
        if (rank < 0) throw new IllegalArgumentException("Rank cannot be negative");
        if (credits < 0) throw new IllegalArgumentException("Credits cannot be negative");
        currencies = Map.copyOf(currencies);
        if (currencies.values().stream().anyMatch(value -> value == null || value < 0)) {
            throw new IllegalArgumentException("Currency balances cannot be negative");
        }
    }
}
