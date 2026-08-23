package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class CatalogLiveMutationServiceTest {
    private static final long ACTIVE_VERSION_ID = 10;
    private final Connection connection = mock(Connection.class);
    private final CatalogVersionRepository versions = mock(CatalogVersionRepository.class);
    private final CatalogChangeJournal journal = mock(CatalogChangeJournal.class);
    private final CatalogSnapshotWriter snapshots = mock(CatalogSnapshotWriter.class);
    private final CatalogLiveEntityWriter live = mock(CatalogLiveEntityWriter.class);
    private final CatalogLiveMutationHook hook = mock(CatalogLiveMutationHook.class);
    private final Gson gson = new Gson();
    private CatalogLiveMutationService service;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(versions.lockRuntimeState(connection))
                .thenReturn(new CatalogRuntimeState(ACTIVE_VERSION_ID, 11, Instant.EPOCH));
        when(versions.loadVersion(connection, ACTIVE_VERSION_ID))
                .thenReturn(new CatalogVersion(
                        ACTIVE_VERSION_ID,
                        CatalogVersionStatus.PUBLISHED,
                        null,
                        4,
                        "Live",
                        1,
                        Instant.EPOCH,
                        1,
                        Instant.EPOCH));
        when(versions.incrementRevision(connection, ACTIVE_VERSION_ID, 4)).thenReturn(5L);
        when(journal.append(
                        eq(connection), eq(ACTIVE_VERSION_ID), eq(5L), eq(7), any(), eq(CatalogChangeSource.UI), any()))
                .thenReturn(21L);
        when(journal.load(connection, 21L))
                .thenReturn(new CatalogChangeGroup(
                        21,
                        ACTIVE_VERSION_ID,
                        5,
                        7,
                        "Change visibility",
                        CatalogChangeSource.UI,
                        Instant.EPOCH,
                        List.of()));
        service = new CatalogLiveMutationService(dataSource, versions, journal, snapshots, live, hook, gson);
    }

    @Test
    void writesLiveAndAuditSnapshotInOneTransactionThenRefreshesCache() throws Exception {
        CatalogPageSnapshot before = page(true);
        CatalogPageSnapshot after = page(false);
        when(versions.loadPage(connection, ACTIVE_VERSION_ID, CatalogPageType.NORMAL, 17))
                .thenReturn(Optional.of(before));

        CatalogLiveMutationResult result = service.apply(new CatalogLiveMutationRequest(
                4,
                7,
                "Change visibility",
                CatalogEntityType.PAGE,
                CatalogPageType.NORMAL,
                17,
                CatalogChangeOperation.UPDATE,
                gson.toJson(after)));

        assertEquals(5, result.revision());
        InOrder transaction = inOrder(live, snapshots, versions, journal, connection, hook);
        transaction.verify(live).apply(eq(connection), any(CatalogChangeEntry.class));
        transaction.verify(snapshots).apply(eq(connection), eq(ACTIVE_VERSION_ID), any(CatalogChangeEntry.class));
        transaction.verify(versions).incrementRevision(connection, ACTIVE_VERSION_ID, 4);
        transaction
                .verify(journal)
                .append(eq(connection), eq(ACTIVE_VERSION_ID), eq(5L), eq(7), any(), eq(CatalogChangeSource.UI), any());
        transaction.verify(connection).commit();
        transaction.verify(hook).afterCommit(any(CatalogChangeEntry.class));
    }

    @Test
    void rollsBackBothRepresentationsWhenTheAuditSnapshotFails() throws Exception {
        when(versions.loadPage(connection, ACTIVE_VERSION_ID, CatalogPageType.NORMAL, 17))
                .thenReturn(Optional.of(page(true)));
        org.mockito.Mockito.doThrow(new java.sql.SQLException("snapshot failed"))
                .when(snapshots)
                .apply(eq(connection), eq(ACTIVE_VERSION_ID), any(CatalogChangeEntry.class));

        org.junit.jupiter.api.Assertions.assertThrows(
                CatalogVersioningException.class,
                () -> service.apply(new CatalogLiveMutationRequest(
                        4,
                        7,
                        "Change visibility",
                        CatalogEntityType.PAGE,
                        CatalogPageType.NORMAL,
                        17,
                        CatalogChangeOperation.UPDATE,
                        gson.toJson(page(false)))));

        verify(connection).rollback();
        verify(hook, org.mockito.Mockito.never()).afterCommit(any());
    }

    @Test
    void visibilityEditIsBuiltFromTheLockedLivePageInsteadOfAStaleClientPayload() throws Exception {
        when(versions.loadPage(connection, ACTIVE_VERSION_ID, CatalogPageType.NORMAL, 17))
                .thenReturn(Optional.of(page(true)));

        service.setPageVisible(7, "Change visibility", CatalogPageType.NORMAL, 17, false);

        ArgumentCaptor<CatalogChangeEntry> change = ArgumentCaptor.forClass(CatalogChangeEntry.class);
        verify(live).apply(eq(connection), change.capture());
        CatalogPageSnapshot committed = gson.fromJson(change.getValue().afterJson(), CatalogPageSnapshot.class);
        assertEquals("Page 17", committed.caption());
        org.junit.jupiter.api.Assertions.assertFalse(committed.visible());
    }

    private static CatalogPageSnapshot page(boolean visible) {
        return new CatalogPageSnapshot(
                CatalogPageType.NORMAL,
                17,
                -1,
                "page_17",
                "Page 17",
                "default_3x3",
                1,
                1,
                1,
                1,
                visible,
                true,
                false,
                "NORMAL",
                false,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                0,
                "");
    }
}
