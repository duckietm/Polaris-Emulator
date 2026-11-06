package polaris.hotel.catalog.versioning;

@FunctionalInterface
public interface CatalogPublicationHooks {
    void afterCommit(CatalogVersionSnapshot publishedSnapshot, long nextDraftVersionId);
}
