package com.eu.habbo.habbohotel.catalog.versioning;

@FunctionalInterface
public interface CatalogLiveMutationHook {
    CatalogLiveMutationHook NO_OP = ignored -> {};

    void afterCommit(CatalogChangeEntry change);
}
