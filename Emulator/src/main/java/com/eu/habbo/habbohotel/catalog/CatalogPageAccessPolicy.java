package com.eu.habbo.habbohotel.catalog;

/**
 * Shared authorization policy for requests that address a catalog page directly.
 */
public final class CatalogPageAccessPolicy {
    private CatalogPageAccessPolicy() {
    }

    public static boolean canAccess(CatalogPage page, int userRank, boolean hasActiveClub) {
        return page != null
                && page.isEnabled()
                && page.getRank() <= userRank
                && (!page.isClubOnly() || hasActiveClub);
    }
}
