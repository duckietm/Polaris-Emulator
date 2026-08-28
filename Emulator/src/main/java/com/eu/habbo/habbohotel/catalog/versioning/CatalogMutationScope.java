package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The offers a batch of mutations can reach.
 *
 * <p>A save used to read and lock the whole catalog - 112,000 offers on a live hotel - to change one
 * row. Pages are cheap and are always read in full, because the rules that judge a page walk the
 * tree. Offers are not: an offer is only read when the batch names it, or when it sits on a page the
 * batch creates or deletes, which is the only way a page edit can change an offer's verdict or block
 * a delete.
 */
public final class CatalogMutationScope {

    private final Map<CatalogPageType, Set<Integer>> offerIds = new EnumMap<>(CatalogPageType.class);
    private final Map<CatalogPageType, Set<Integer>> pageIds = new EnumMap<>(CatalogPageType.class);

    private CatalogMutationScope() {}

    public static CatalogMutationScope of(Collection<CatalogLiveMutationRequest> requests) {
        CatalogMutationScope scope = new CatalogMutationScope();

        for (CatalogLiveMutationRequest request : requests) {
            Map<CatalogPageType, Set<Integer>> target =
                    request.entityType() == CatalogEntityType.PAGE ? scope.pageIds : scope.offerIds;
            target.computeIfAbsent(request.catalogType(), ignored -> new HashSet<>())
                    .add(request.entityId());
        }

        return scope;
    }

    /** Offers named directly by the batch. */
    public Set<Integer> offerIds(CatalogPageType catalogType) {
        return Set.copyOf(offerIds.getOrDefault(catalogType, Set.of()));
    }

    /** Pages named by the batch; their offers are read so a page edit still sees them. */
    public Set<Integer> pageIds(CatalogPageType catalogType) {
        return Set.copyOf(pageIds.getOrDefault(catalogType, Set.of()));
    }

    public boolean isEmpty(CatalogPageType catalogType) {
        return offerIds(catalogType).isEmpty() && pageIds(catalogType).isEmpty();
    }
}
