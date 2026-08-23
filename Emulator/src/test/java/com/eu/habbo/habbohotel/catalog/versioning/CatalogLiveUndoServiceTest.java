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
import org.junit.jupiter.api.Test;

class CatalogLiveUndoServiceTest {
    @Test
    void reversesTheWholeOperationInLiveAndKeepsAnAuditEntry() throws Exception {
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        CatalogVersionRepository versions = mock(CatalogVersionRepository.class);
        CatalogChangeJournal journal = mock(CatalogChangeJournal.class);
        CatalogOperationRepository operations = mock(CatalogOperationRepository.class);
        CatalogLiveEntityWriter live = mock(CatalogLiveEntityWriter.class);
        CatalogLiveMutationHook hook = mock(CatalogLiveMutationHook.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(versions.lockRuntimeState(connection)).thenReturn(new CatalogRuntimeState(10, 11, Instant.EPOCH));
        when(versions.loadSnapshot(connection, 10))
                .thenReturn(new CatalogVersionSnapshot(
                        new CatalogVersion(
                                10,
                                CatalogVersionStatus.PUBLISHED,
                                null,
                                4,
                                "Live",
                                1,
                                Instant.EPOCH,
                                1,
                                Instant.EPOCH),
                        List.of(),
                        List.of()));
        CatalogChangeEntry original = new CatalogChangeEntry(
                1,
                CatalogEntityType.PAGE,
                CatalogPageType.NORMAL,
                17,
                CatalogChangeOperation.UPDATE,
                "before",
                "after");
        when(journal.load(connection, 21))
                .thenReturn(new CatalogChangeGroup(
                        21, 10, 4, 7, "Edit", CatalogChangeSource.UI, Instant.EPOCH, List.of(original)));
        when(journal.hasLaterChangesToSameEntities(eq(connection), any())).thenReturn(false);
        when(versions.incrementRevision(connection, 10, 4)).thenReturn(5L);
        when(journal.append(eq(connection), eq(10L), eq(5L), eq(7), any(), eq(CatalogChangeSource.UNDO), any()))
                .thenReturn(22L);
        when(operations.findForUpdate(connection, "undo-21", 7)).thenReturn(Optional.empty());

        long revision = new CatalogLiveUndoService(
                        dataSource, versions, journal, (CatalogLiveSnapshotRepository) null, live, hook, operations)
                .undo(21, 7, 4, "undo-21");

        assertEquals(5, revision);
        verify(live)
                .apply(
                        eq(connection),
                        org.mockito.ArgumentMatchers.argThat(
                                change -> change.beforeJson().equals("after")
                                        && change.afterJson().equals("before")));
        verify(journal).append(eq(connection), eq(10L), eq(5L), eq(7), any(), eq(CatalogChangeSource.UNDO), any());
        verify(operations).insert(eq(connection), any(CatalogOperationRecord.class));
        verify(connection).commit();
        verify(hook).afterCommit(any(CatalogChangeEntry.class));
    }
}
