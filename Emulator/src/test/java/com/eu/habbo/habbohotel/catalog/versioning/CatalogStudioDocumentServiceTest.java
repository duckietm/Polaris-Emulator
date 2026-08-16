package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class CatalogStudioDocumentServiceTest {
    private final CatalogStudioDocumentService service = new CatalogStudioDocumentService();
    private final CatalogVersionSnapshot snapshot = mock(CatalogVersionSnapshot.class);

    @Test
    void rejectsUnknownTransferFormats() {
        IllegalArgumentException exportFailure =
                assertThrows(IllegalArgumentException.class, () -> service.export(snapshot, "CSV"));
        IllegalArgumentException importFailure =
                assertThrows(IllegalArgumentException.class, () -> service.dryRun(snapshot, "CSV", ""));

        assertEquals("Unsupported Catalog Studio export format: CSV", exportFailure.getMessage());
        assertEquals("Unsupported Catalog Studio document format: CSV", importFailure.getMessage());
    }
}
