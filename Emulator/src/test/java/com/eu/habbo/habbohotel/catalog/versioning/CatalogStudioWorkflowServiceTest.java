package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogStudioWorkflowServiceTest {
    private static final long ACTIVE_ID = 1;
    private static final long DRAFT_ID = 2;
    private static final long NEXT_DRAFT_ID = 3;
    private static final long REVISION = 5;
    private static final int ACTOR_ID = 7;

    private final Connection connection = mock(Connection.class);
    private final FakeVersions versions = new FakeVersions();
    private final FakeLocks locks = new FakeLocks();
    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        versions.activeSnapshot = snapshot("100");
        versions.snapshot = snapshot("404");
    }

    @Test
    void validatesOnlyTheCurrentDraftRevision() {
        CatalogDraftValidationService service = new CatalogDraftValidationService(
                dataSource,
                versions,
                ignored -> new CatalogValidationReferenceData(Set.of(100), Set.of(5), Map.of(), Set.of("root")));

        CatalogDraftValidationResult result = service.validate(DRAFT_ID, REVISION);

        assertEquals(REVISION, result.revision());
        assertFalse(result.report().valid());
        assertEquals("OFFER_ITEM_MISSING", result.report().issues().getFirst().code());
        assertThrows(CatalogConcurrentModificationException.class, () -> service.validate(DRAFT_ID, REVISION - 1));
    }

    @Test
    void discardingArchivesTheOldDraftAndClonesThePublishedCatalog() {
        CatalogDraftLifecycleService service = new CatalogDraftLifecycleService(dataSource, versions, locks);

        CatalogDraftDiscardResult result = service.discard(DRAFT_ID, REVISION, ACTOR_ID, "Clean shared draft");

        assertEquals(ACTIVE_ID, result.activeVersionId());
        assertEquals(NEXT_DRAFT_ID, result.draftVersionId());
        assertEquals(0, result.revision());
        assertEquals(List.of(DRAFT_ID), versions.archived);
        assertEquals(ACTIVE_ID, versions.cloneSource);
        assertEquals(ACTIVE_ID, versions.runtimeActive);
        assertEquals(NEXT_DRAFT_ID, versions.runtimeDraft);
        assertEquals(List.of(DRAFT_ID), locks.releasedVersions);
    }

    private static CatalogVersionSnapshot snapshot(String itemIds) {
        CatalogPageSnapshot root = new CatalogPageSnapshot(
                1, -1, "root", "Root", "root", 0, 0, 1, 0, true, true, false, "NORMAL", false, "", "", "", "", "", "",
                "", 0, "");
        CatalogOfferSnapshot offer =
                new CatalogOfferSnapshot(10, itemIds, 1, "Chair", 3, 0, 0, 1, 0, 0, 10, 0, "", true, false);
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

    private static final class FakeVersions implements CatalogVersionRepository {
        private CatalogVersionSnapshot snapshot;
        private CatalogVersionSnapshot activeSnapshot;
        private final java.util.ArrayList<Long> archived = new java.util.ArrayList<>();
        private long cloneSource;
        private long runtimeActive;
        private long runtimeDraft;

        @Override
        public CatalogRuntimeState lockRuntimeState(Connection ignored) {
            return new CatalogRuntimeState(ACTIVE_ID, DRAFT_ID, Instant.EPOCH);
        }

        @Override
        public CatalogVersionSnapshot loadSnapshot(Connection ignored, long versionId) {
            return versionId == ACTIVE_ID ? activeSnapshot : snapshot;
        }

        @Override
        public long cloneAsDraft(Connection ignored, long sourceVersionId, int actorId, String label) {
            cloneSource = sourceVersionId;
            return NEXT_DRAFT_ID;
        }

        @Override
        public void archiveVersion(Connection ignored, long versionId) {
            archived.add(versionId);
        }

        @Override
        public void updateRuntimePointers(Connection ignored, long activeVersionId, long draftVersionId) {
            runtimeActive = activeVersionId;
            runtimeDraft = draftVersionId;
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

        @Override
        public void markPublished(Connection ignored, long versionId, int actorId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeLocks implements CatalogLockRepository {
        private final java.util.ArrayList<Long> releasedVersions = new java.util.ArrayList<>();

        @Override
        public void releaseAll(Connection ignored, long versionId) {
            releasedVersions.add(versionId);
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
