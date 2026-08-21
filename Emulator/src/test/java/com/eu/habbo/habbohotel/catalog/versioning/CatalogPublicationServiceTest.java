package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogPublicationServiceTest {
    private static final long ACTIVE_ID = 1;
    private static final long DRAFT_ID = 2;
    private static final long NEXT_DRAFT_ID = 3;
    private static final long REVISION = 5;
    private static final int ACTOR_ID = 7;

    private final List<String> events = new ArrayList<>();
    private final Connection connection = mock(Connection.class);
    private final FakeVersionRepository versions = new FakeVersionRepository();
    private final FakeProjection projection = new FakeProjection();
    private final FakeLocks locks = new FakeLocks();
    private final FakeReconciler reconciler = new FakeReconciler();
    private final CatalogPublicationHooks hooks = (published, nextDraftId) -> events.add("afterCommit:" + nextDraftId);
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        doAnswer(invocation -> {
                    events.add("commit");
                    return null;
                })
                .when(connection)
                .commit();
        doAnswer(invocation -> {
                    events.add("rollback");
                    return null;
                })
                .when(connection)
                .rollback();
        versions.snapshot = snapshot();
        versions.activeSnapshot = snapshotWithCredits("100", 2);
        reconciler.result = null;
    }

    @Test
    void validationFailureLeavesLiveCatalogAndVersionPointersUntouched() throws Exception {
        versions.snapshot = snapshot("404");
        CatalogPublicationService service = service(referenceData(Set.of(100), Map.of(10, 1)));

        CatalogPublicationResult result = service.publish(request());

        assertFalse(result.published());
        assertFalse(result.validation().valid());
        assertEquals(List.of("lockRuntime", "loadSnapshot:2", "loadSnapshot:1", "reconcile", "rollback"), events);
        assertEquals(0, projection.calls);
        verify(connection, never()).commit();
    }

    @Test
    void inheritedValidationIssuesDoNotBlockPublication() {
        versions.activeSnapshot = snapshotWithCredits("404", 2);
        versions.snapshot = snapshot("404");
        CatalogPublicationService service = service(referenceData(Set.of(100), Map.of(10, 1)));

        CatalogPublicationResult result = service.publish(request());

        assertTrue(result.published());
        assertTrue(result.validation().valid());
        assertEquals(1, projection.calls);
    }

    @Test
    void projectionFailureRollsBackAndDoesNotReloadOrBroadcast() throws Exception {
        projection.failure = new SQLException("projection failed");
        CatalogPublicationService service = service(referenceData(Set.of(100), Map.of(10, 1)));

        assertThrows(CatalogPublicationException.class, () -> service.publish(request()));

        assertEquals(
                List.of("lockRuntime", "loadSnapshot:2", "loadSnapshot:1", "reconcile", "project", "rollback"), events);
        verify(connection, never()).commit();
        assertFalse(events.stream().anyMatch(event -> event.startsWith("afterCommit")));
    }

    @Test
    void publicationProjectsThenSwitchesVersionsAndRunsHooksOnlyAfterCommit() {
        CatalogPublicationService service = service(referenceData(Set.of(100), Map.of(10, 1)));

        CatalogPublicationResult result = service.publish(request());

        assertTrue(result.published());
        assertTrue(result.validation().valid());
        assertEquals(DRAFT_ID, result.activeVersionId());
        assertEquals(NEXT_DRAFT_ID, result.draftVersionId());
        assertEquals(
                List.of(
                        "lockRuntime",
                        "loadSnapshot:2",
                        "loadSnapshot:1",
                        "reconcile",
                        "project",
                        "archive:1",
                        "publish:2",
                        "clone:2",
                        "pointers:2:3",
                        "releaseLocks:2",
                        "commit",
                        "afterCommit:3"),
                events);
    }

    @Test
    void publicationProjectsTheAutomaticallyReconciledLiveChanges() {
        CatalogVersionSnapshot reconciled = snapshotWithCredits(25);
        reconciler.result = new CatalogLiveReconciliationResult(reconciled, 1, List.of());
        CatalogPublicationService service = service(referenceData(Set.of(100), Map.of(10, 1)));

        CatalogPublicationResult result = service.publish(request());

        assertTrue(result.published());
        assertEquals(1, result.importedChanges());
        assertEquals(25, projection.snapshot.offer(10).orElseThrow().costCredits());
    }

    @Test
    void publicationDoesNotCreateARedundantVersionWhenLiveAndDraftAreUnchanged() throws Exception {
        versions.activeSnapshot = snapshot();
        CatalogPublicationService service = service(referenceData(Set.of(100), Map.of(10, 1)));

        CatalogPublicationResult result = service.publish(request());

        assertFalse(result.published());
        assertTrue(result.noChanges());
        assertEquals(ACTIVE_ID, result.activeVersionId());
        assertEquals(DRAFT_ID, result.draftVersionId());
        assertEquals(List.of("lockRuntime", "loadSnapshot:2", "loadSnapshot:1", "reconcile", "commit"), events);
        assertEquals(0, projection.calls);
        assertFalse(events.stream().anyMatch(event -> event.startsWith("afterCommit")));
    }

    @Test
    void liveConflictRollsBackWithoutProjectingOrSwitchingVersions() throws Exception {
        CatalogMergeConflict conflict =
                new CatalogMergeConflict(CatalogEntityType.OFFER, CatalogPageType.NORMAL, 10, "costCredits");
        reconciler.result = new CatalogLiveReconciliationResult(versions.snapshot, 0, List.of(conflict));
        CatalogPublicationService service = service(referenceData(Set.of(100), Map.of(10, 1)));

        CatalogPublicationResult result = service.publish(request());

        assertFalse(result.published());
        assertFalse(result.noChanges());
        assertEquals(List.of(conflict), result.conflicts());
        assertEquals(List.of("lockRuntime", "loadSnapshot:2", "loadSnapshot:1", "reconcile", "rollback"), events);
        assertEquals(0, projection.calls);
        verify(connection, never()).commit();
    }

    private CatalogPublicationService service(CatalogValidationReferenceData referenceData) {
        return new CatalogPublicationService(
                dataSource, versions, connection -> referenceData, reconciler, projection, locks, hooks);
    }

    private static CatalogPublicationRequest request() {
        return new CatalogPublicationRequest(DRAFT_ID, REVISION, ACTOR_ID, "Draft after publication");
    }

    private static CatalogValidationReferenceData referenceData(
            Set<Integer> itemIds, Map<Integer, Integer> liveLimitedSells) {
        return new CatalogValidationReferenceData(itemIds, Set.of(5), liveLimitedSells, Set.of("root", "default_3x3"));
    }

    private static CatalogVersionSnapshot snapshot() {
        return snapshot("100");
    }

    private static CatalogVersionSnapshot snapshot(String itemIds) {
        CatalogPageSnapshot root = new CatalogPageSnapshot(
                1, -1, "root", "Root", "root", 0, 0, 1, 0, true, true, false, "NORMAL", false, "", "", "", "", "", "",
                "", 0, "");
        CatalogOfferSnapshot offer =
                new CatalogOfferSnapshot(10, itemIds, 1, "Chair", 3, 0, 0, 1, 2, 0, 10, 0, "", true, false);
        CatalogVersion version = new CatalogVersion(
                DRAFT_ID,
                CatalogVersionStatus.DRAFT,
                ACTIVE_ID,
                REVISION,
                "Shared draft",
                ACTOR_ID,
                Instant.parse("2026-08-02T11:00:00Z"),
                null,
                null);
        return new CatalogVersionSnapshot(version, List.of(root), List.of(offer));
    }

    private static CatalogVersionSnapshot snapshotWithCredits(int credits) {
        return snapshotWithCredits("100", credits);
    }

    private static CatalogVersionSnapshot snapshotWithCredits(String itemIds, int credits) {
        CatalogVersionSnapshot original = snapshot(itemIds);
        CatalogOfferSnapshot offer = original.offer(10).orElseThrow();
        CatalogOfferSnapshot changed = new CatalogOfferSnapshot(
                offer.catalogType(),
                offer.offerId(),
                offer.itemIds(),
                offer.pageId(),
                offer.catalogName(),
                credits,
                offer.costPoints(),
                offer.pointsType(),
                offer.amount(),
                offer.limitedStack(),
                offer.orderNumber(),
                offer.offerIdClient(),
                offer.songId(),
                offer.extradata(),
                offer.haveOffer(),
                offer.clubOnly());
        return new CatalogVersionSnapshot(original.version(), original.pages(), List.of(changed));
    }

    private final class FakeVersionRepository implements CatalogVersionRepository {
        private CatalogVersionSnapshot snapshot;
        private CatalogVersionSnapshot activeSnapshot;

        @Override
        public CatalogRuntimeState lockRuntimeState(Connection ignored) {
            events.add("lockRuntime");
            return new CatalogRuntimeState(ACTIVE_ID, DRAFT_ID, Instant.EPOCH);
        }

        @Override
        public CatalogVersionSnapshot loadSnapshot(Connection ignored, long versionId) {
            events.add("loadSnapshot:" + versionId);
            return versionId == ACTIVE_ID ? activeSnapshot : snapshot;
        }

        @Override
        public long cloneAsDraft(Connection ignored, long sourceVersionId, int actorId, String label) {
            events.add("clone:" + sourceVersionId);
            return NEXT_DRAFT_ID;
        }

        @Override
        public void archiveVersion(Connection ignored, long versionId) {
            events.add("archive:" + versionId);
        }

        @Override
        public void markPublished(Connection ignored, long versionId, int actorId) {
            events.add("publish:" + versionId);
        }

        @Override
        public void updateRuntimePointers(Connection ignored, long activeVersionId, long draftVersionId) {
            events.add("pointers:" + activeVersionId + ":" + draftVersionId);
        }

        @Override
        public long nextPageId(Connection ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long nextOfferId(Connection ignored) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long incrementRevision(Connection ignored, long versionId, long expectedRevision) {
            throw new UnsupportedOperationException();
        }
    }

    private final class FakeProjection implements CatalogLiveProjection {
        private int calls;
        private SQLException failure;
        private CatalogVersionSnapshot snapshot;

        @Override
        public void replace(Connection ignored, CatalogVersionSnapshot snapshot) throws SQLException {
            calls++;
            this.snapshot = snapshot;
            events.add("project");
            if (failure != null) throw failure;
        }
    }

    private final class FakeReconciler implements CatalogLiveReconciler {
        private CatalogLiveReconciliationResult result;

        @Override
        public CatalogLiveReconciliationResult reconcile(
                Connection ignored, CatalogVersionSnapshot active, CatalogVersionSnapshot draft, int actorId) {
            events.add("reconcile");
            return result == null ? new CatalogLiveReconciliationResult(draft, 0, List.of()) : result;
        }
    }

    private final class FakeLocks implements CatalogLockRepository {
        @Override
        public void releaseAll(Connection ignored, long versionId) {
            events.add("releaseLocks:" + versionId);
        }

        @Override
        public CatalogLockRecord acquire(
                long versionId, CatalogLockKey key, int ownerId, UUID token, Instant now, Instant expiresAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CatalogLockRecord> renew(
                long versionId, CatalogLockKey key, int ownerId, UUID token, Instant now, Instant expiresAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean release(long versionId, CatalogLockKey key, int ownerId, UUID token) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void releaseAll(long versionId) {
            throw new UnsupportedOperationException();
        }
    }
}
