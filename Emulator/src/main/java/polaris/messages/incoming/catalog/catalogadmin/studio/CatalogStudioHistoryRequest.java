package polaris.messages.incoming.catalog.catalogadmin.studio;

public record CatalogStudioHistoryRequest(long draftVersionId, int offset, int limit) {}
