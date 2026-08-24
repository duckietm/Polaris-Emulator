package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogLiveChangeSetServiceTest {
    private final Connection connection = mock(Connection.class);
    private final CatalogVersionRepository versions = mock(CatalogVersionRepository.class);
    private final CatalogSnapshotWriter snapshots = mock(CatalogSnapshotWriter.class);
    private final CatalogLiveEntityWriter live = mock(CatalogLiveEntityWriter.class);
    private final CatalogChangeJournal journal = mock(CatalogChangeJournal.class);
    private final CatalogStudioDocumentService documents = mock(CatalogStudioDocumentService.class);
    private final CatalogOperationRepository operations = mock(CatalogOperationRepository.class);
    private final CatalogLiveMutationHook hook = mock(CatalogLiveMutationHook.class);
    private CatalogLiveChangeSetService service;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(versions.lockRuntimeState(connection)).thenReturn(new CatalogRuntimeState(10, 11, Instant.EPOCH));
        when(versions.loadSnapshot(connection, 10)).thenReturn(snapshot());
        when(versions.incrementRevision(connection, 10, 4)).thenReturn(5L);
        when(operations.findForUpdate(connection, "sql-1", 7)).thenReturn(Optional.empty());
        service = new CatalogLiveChangeSetService(
                dataSource, versions, snapshots, live, journal, documents, operations, hook);
    }

    @Test
    void appliesTheConfirmedDryRunToLiveAndHistoryAtomically() throws Exception {
        CatalogChangeEntry change = new CatalogChangeEntry(
                0,
                CatalogEntityType.PAGE,
                CatalogPageType.NORMAL,
                17,
                CatalogChangeOperation.UPDATE,
                "{\"visible\":true}",
                "{\"visible\":false}");
        CatalogImportDryRun dryRun =
                new CatalogImportDryRun(CatalogChangeSource.SQL, 10, 4, "UPDATE", List.of(change), "abc");
        when(documents.dryRun(any(), eq("SQL"), eq("UPDATE"))).thenReturn(dryRun);

        CatalogChangeSetApplyResult result = service.apply("sql-1", 7, "SQL", "UPDATE", "abc", "Repair catalog");

        assertEquals(5, result.revision());
        assertEquals(1, result.changedEntities());
        verify(live).apply(connection, change);
        verify(journal).append(connection, 10, 5, 7, "Repair catalog", CatalogChangeSource.SQL, List.of(change));
        verify(operations).insert(eq(connection), any(CatalogOperationRecord.class));
        verify(connection).commit();
        verify(hook).afterCommit(change);
    }

    private static CatalogVersionSnapshot snapshot() {
        return new CatalogVersionSnapshot(
                new CatalogVersion(
                        10, CatalogVersionStatus.PUBLISHED, null, 4, "Live", 1, Instant.EPOCH, 1, Instant.EPOCH),
                List.of(),
                List.of());
    }
}
