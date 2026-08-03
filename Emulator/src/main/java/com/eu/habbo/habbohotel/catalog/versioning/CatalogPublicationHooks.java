package com.eu.habbo.habbohotel.catalog.versioning;

@FunctionalInterface
public interface CatalogPublicationHooks {
    void afterCommit(CatalogVersionSnapshot publishedSnapshot, long nextDraftVersionId);
}
