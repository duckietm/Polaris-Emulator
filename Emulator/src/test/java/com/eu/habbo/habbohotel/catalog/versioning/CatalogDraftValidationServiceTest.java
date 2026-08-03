package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class CatalogDraftValidationServiceTest {
    private static final long ACTIVE_ID = 1;
    private static final long DRAFT_ID = 2;
    private static final long REVISION = 5;

    @Test
    void inheritedValidationIssuesAreNotReportedAsDraftRegressions() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        CatalogVersionRepository versions = mock(CatalogVersionRepository.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(versions.lockRuntimeState(connection))
                .thenReturn(new CatalogRuntimeState(ACTIVE_ID, DRAFT_ID, Instant.EPOCH));
        when(versions.loadSnapshot(connection, ACTIVE_ID))
                .thenReturn(snapshot(ACTIVE_ID, CatalogVersionStatus.PUBLISHED));
        when(versions.loadSnapshot(connection, DRAFT_ID)).thenReturn(snapshot(DRAFT_ID, CatalogVersionStatus.DRAFT));

        CatalogValidationReferenceData referenceData =
                new CatalogValidationReferenceData(Set.of(100), Set.of(5), Map.of(), Set.of("root"));
        CatalogDraftValidationService service =
                new CatalogDraftValidationService(dataSource, versions, ignored -> referenceData);

        CatalogDraftValidationResult result = service.validate(DRAFT_ID, REVISION);

        assertTrue(result.report().valid());
        verify(versions).loadSnapshot(connection, ACTIVE_ID);
        verify(versions).loadSnapshot(connection, DRAFT_ID);
    }

    private static CatalogVersionSnapshot snapshot(long versionId, CatalogVersionStatus status) {
        CatalogVersion version = new CatalogVersion(
                versionId,
                status,
                ACTIVE_ID,
                REVISION,
                "Catalog",
                7,
                Instant.parse("2026-08-02T11:00:00Z"),
                null,
                null);
        CatalogPageSnapshot root = new CatalogPageSnapshot(
                1, -1, "root", "Root", "root", 0, 0, 1, 0, true, true, false, "NORMAL", false, "", "", "", "", "", "",
                "", 0, "");
        CatalogOfferSnapshot invalidLegacyOffer =
                new CatalogOfferSnapshot(10, "404", 1, "Legacy", 3, 0, 0, 1, 2, 0, 10, 0, "", true, false);
        return new CatalogVersionSnapshot(version, List.of(root), List.of(invalidLegacyOffer));
    }
}
