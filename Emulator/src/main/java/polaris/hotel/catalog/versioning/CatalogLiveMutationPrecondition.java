package polaris.hotel.catalog.versioning;

@FunctionalInterface
public interface CatalogLiveMutationPrecondition {
    CatalogLiveMutationPrecondition NONE = ignored -> {};

    void validate(CatalogVersionSnapshot liveSnapshot);
}
