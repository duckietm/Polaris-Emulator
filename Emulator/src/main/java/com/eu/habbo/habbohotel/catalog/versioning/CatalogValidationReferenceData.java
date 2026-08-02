package com.eu.habbo.habbohotel.catalog.versioning;

import java.util.Map;
import java.util.Set;

public record CatalogValidationReferenceData(
        Set<Integer> itemDefinitionIds,
        Set<Integer> currencyTypes,
        Map<Integer, Integer> liveLimitedSells,
        Set<String> supportedLayouts) {

    public CatalogValidationReferenceData {
        itemDefinitionIds = Set.copyOf(itemDefinitionIds);
        currencyTypes = Set.copyOf(currencyTypes);
        liveLimitedSells = Map.copyOf(liveLimitedSells);
        supportedLayouts = Set.copyOf(supportedLayouts);
    }

    public CatalogValidator validator() {
        return new CatalogValidator(itemDefinitionIds, currencyTypes, liveLimitedSells, supportedLayouts);
    }
}
