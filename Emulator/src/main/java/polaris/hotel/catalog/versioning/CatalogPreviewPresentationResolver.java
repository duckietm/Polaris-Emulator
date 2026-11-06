package polaris.hotel.catalog.versioning;

@FunctionalInterface
public interface CatalogPreviewPresentationResolver {
    CatalogPreviewPresentation resolve(CatalogOfferSnapshot offer);
}
