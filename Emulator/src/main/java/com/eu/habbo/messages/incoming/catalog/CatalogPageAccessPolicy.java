package com.eu.habbo.messages.incoming.catalog;

final class CatalogPageAccessPolicy {
    private CatalogPageAccessPolicy() {}

    static boolean canOpen(int pageRank, int userRank, boolean enabled, boolean canSeeCatalogIds) {
        return pageRank <= userRank && (enabled || canSeeCatalogIds);
    }
}
