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
    private final CatalogLiveSnapshotRepository liveSnapshots = mock(CatalogLiveSnapshotRepository.class);
    private final CatalogLiveEntityWriter live = mock(CatalogLiveEntityWriter.class);
    private final CatalogLiveMutationHook hook = mock(CatalogLiveMutationHook.class);
    private final CatalogOperationRepository operations = mock(CatalogOperationRepository.class);
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
        when(versions.loadSnapshot(connection, ACTIVE_VERSION_ID))
                .thenReturn(new CatalogVersionSnapshot(
                        new CatalogVersion(
                                ACTIVE_VERSION_ID,
                                CatalogVersionStatus.PUBLISHED,
                                null,
                                4,
                                "Live",
                                1,
                                Instant.EPOCH,
                                1,
                                Instant.EPOCH),
                        List.of(page(true)),
                        List.of()));
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
    void opensThePhysicalLiveCatalogInsteadOfTheVersionedRecoveryCopy() throws Exception {
        CatalogVersionSnapshot physicalLive = new CatalogVersionSnapshot(
                versions.loadVersion(connection, ACTIVE_VERSION_ID), List.of(page(false)), List.of());
        when(liveSnapshots.load(eq(connection), any(CatalogVersion.class))).thenReturn(physicalLive);
        CatalogLiveMutationService physicalService = new CatalogLiveMutationService(
                serviceDataSource(), versions, journal, snapshots, liveSnapshots, live, hook, null, gson);

        CatalogVersionSnapshot opened = physicalService.loadLive();

        assertEquals(physicalLive, opened);
        verify(liveSnapshots).load(eq(connection), any(CatalogVersion.class));
        verify(versions, org.mockito.Mockito.never()).loadSnapshot(connection, ACTIVE_VERSION_ID);
    }

    @Test
    void recordsTheClientOperationIdInTheSameLiveTransaction() throws Exception {
        CatalogVersionSnapshot physicalLive = new CatalogVersionSnapshot(
                versions.loadVersion(connection, ACTIVE_VERSION_ID), List.of(page(true)), List.of());
        when(liveSnapshots.load(eq(connection), any(CatalogVersion.class))).thenReturn(physicalLive);
        when(operations.findForUpdate(connection, "save-page-1", 7)).thenReturn(Optional.empty());
        CatalogLiveMutationService physicalService = new CatalogLiveMutationService(
                serviceDataSource(), versions, journal, snapshots, liveSnapshots, live, hook, null, operations, gson);

        physicalService.apply(new CatalogLiveMutationRequest(
                4,
                7,
                "Save page",
                CatalogEntityType.PAGE,
                CatalogPageType.NORMAL,
                17,
                CatalogChangeOperation.UPDATE,
                gson.toJson(page(false)),
                "save-page-1"));

        ArgumentCaptor<CatalogOperationRecord> record = ArgumentCaptor.forClass(CatalogOperationRecord.class);
        verify(operations).insert(eq(connection), record.capture());
        assertEquals("save-page-1", record.getValue().operationId());
        org.junit.jupiter.api.Assertions.assertFalse(
                record.getValue().requestFingerprint().isBlank());
        verify(connection).commit();
    }

    private DataSource serviceDataSource() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        return dataSource;
    }

    @Test
    void writesLiveAndHistoryInOneTransactionThenRefreshesCache() throws Exception {
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
        InOrder transaction = inOrder(live, versions, journal, connection, hook);
        transaction.verify(live).apply(eq(connection), any(CatalogChangeEntry.class));
        transaction.verify(versions).incrementRevision(connection, ACTIVE_VERSION_ID, 4);
        transaction
                .verify(journal)
                .append(eq(connection), eq(ACTIVE_VERSION_ID), eq(5L), eq(7), any(), eq(CatalogChangeSource.UI), any());
        transaction.verify(connection).commit();
        transaction.verify(hook).afterCommit(any(CatalogChangeEntry.class));
    }

    @Test
    void rollsBackWhenTheLiveWriteFails() throws Exception {
        when(versions.loadPage(connection, ACTIVE_VERSION_ID, CatalogPageType.NORMAL, 17))
                .thenReturn(Optional.of(page(true)));
        org.mockito.Mockito.doThrow(new java.sql.SQLException("live write failed"))
                .when(live)
                .apply(eq(connection), any(CatalogChangeEntry.class));

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

    @Test
    void createsAnOfferAndUpdatesAnotherEntityInOneLiveRevision() throws Exception {
        when(versions.nextOfferId(connection, CatalogPageType.NORMAL)).thenReturn(42L);
        CatalogDraftOfferData offer =
                new CatalogDraftOfferData("12", 17, "chair", 3, 0, 0, 1, 0, 1, -1, 0, "", true, false);

        CatalogLiveMutationBatchResult result = service.applyBatch(List.of(
                new CatalogLiveMutationRequest(
                        -1,
                        7,
                        "Import catalog changes",
                        CatalogEntityType.OFFER,
                        CatalogPageType.NORMAL,
                        0,
                        CatalogChangeOperation.CREATE,
                        gson.toJson(offer)),
                new CatalogLiveMutationRequest(
                        -1,
                        7,
                        "Import catalog changes",
                        CatalogEntityType.PAGE,
                        CatalogPageType.NORMAL,
                        17,
                        CatalogChangeOperation.UPDATE,
                        gson.toJson(page(false)))));

        assertEquals(5, result.revision());
        assertEquals(42, result.changes().getFirst().entityId());
        verify(connection, org.mockito.Mockito.times(1)).commit();
        verify(journal)
                .append(
                        eq(connection),
                        eq(ACTIVE_VERSION_ID),
                        eq(5L),
                        eq(7),
                        eq("Import catalog changes"),
                        eq(CatalogChangeSource.UI),
                        org.mockito.ArgumentMatchers.argThat(changes -> changes.size() == 2));
    }

    @Test
    void validatesAgainstTheLockedLiveSnapshotBeforeWriting() throws Exception {
        CatalogLiveMutationRequest request = new CatalogLiveMutationRequest(
                -1,
                7,
                "Delete page",
                CatalogEntityType.PAGE,
                CatalogPageType.NORMAL,
                17,
                CatalogChangeOperation.DELETE,
                null);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.apply(request, liveSnapshot -> {
                    if (liveSnapshot.offers().stream()
                            .anyMatch(offer -> offer.catalogType() == CatalogPageType.NORMAL && offer.pageId() == 17)) {
                        throw new IllegalArgumentException("Page still contains offers");
                    }
                    throw new IllegalArgumentException("Page still contains child pages");
                }));

        verify(live, org.mockito.Mockito.never()).apply(any(), any());
        verify(connection).rollback();
    }

    @Test
    void loadsTheActiveRecoverySnapshotForEditorReads() throws Exception {
        CatalogVersionSnapshot snapshot = service.loadLive();

        assertEquals(ACTIVE_VERSION_ID, snapshot.version().id());
        assertEquals(
                "Page 17",
                snapshot.page(CatalogPageType.NORMAL, 17).orElseThrow().caption());
        verify(connection).commit();
    }

    @Test
    void offerPatchUsesTheLockedLiveOfferAndChangesOnlyRequestedFields() throws Exception {
        CatalogOfferSnapshot offer = new CatalogOfferSnapshot(
                CatalogPageType.NORMAL, 42, "12", 17, "chair", 3, 0, 0, 1, 0, 2, -1, 0, "", true, false);
        when(versions.loadSnapshot(connection, ACTIVE_VERSION_ID))
                .thenReturn(new CatalogVersionSnapshot(
                        new CatalogVersion(
                                ACTIVE_VERSION_ID,
                                CatalogVersionStatus.PUBLISHED,
                                null,
                                4,
                                "Live",
                                1,
                                Instant.EPOCH,
                                1,
                                Instant.EPOCH),
                        List.of(page(true)),
                        List.of(offer)));
        when(versions.loadOffer(connection, ACTIVE_VERSION_ID, CatalogPageType.NORMAL, 42))
                .thenReturn(Optional.of(offer));

        service.updateOffer(
                7,
                "Reorder offer",
                CatalogPageType.NORMAL,
                42,
                current -> CatalogSnapshotPatch.setOfferOrder(current, 9),
                CatalogChangeOperation.MOVE);

        ArgumentCaptor<CatalogChangeEntry> change = ArgumentCaptor.forClass(CatalogChangeEntry.class);
        verify(live).apply(eq(connection), change.capture());
        CatalogOfferSnapshot committed = gson.fromJson(change.getValue().afterJson(), CatalogOfferSnapshot.class);
        assertEquals("chair", committed.catalogName());
        assertEquals(9, committed.orderNumber());
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
