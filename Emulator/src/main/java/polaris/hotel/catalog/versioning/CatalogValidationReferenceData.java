package polaris.hotel.catalog.versioning;

import java.util.Map;
import java.util.Set;

public record CatalogValidationReferenceData(
        Set<Integer> itemDefinitionIds,
        Set<Integer> currencyTypes,
        Map<Integer, Integer> liveLimitedSells,
        Set<String> supportedLayouts) {

    public CatalogValidationReferenceData {
        itemDefinitionIds = CatalogLookupCopies.set(itemDefinitionIds);
        currencyTypes = CatalogLookupCopies.set(currencyTypes);
        liveLimitedSells = CatalogLookupCopies.map(liveLimitedSells);
        supportedLayouts = Set.copyOf(supportedLayouts);
    }

    public CatalogValidator validator() {
        return new CatalogValidator(itemDefinitionIds, currencyTypes, liveLimitedSells, supportedLayouts);
    }
}
