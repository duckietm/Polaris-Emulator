package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogAdminSmartSaveServiceTest {
    private static final long DRAFT_ID = 2;
    private static final int ACTOR_ID = 7;
    private static final UUID LOCK_TOKEN = UUID.fromString("2ba46713-065e-4731-b9f1-7b7a4c18e1af");
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    private final Gson gson = new Gson();
    private final Connection connection = mock(Connection.class);
    private final FakeVersionRepository versions = new FakeVersionRepository();
    private final FakeJournal journal = new FakeJournal();
    private final FakeWriter writer = new FakeWriter();
    private final FakeLockRepository locks = new FakeLockRepository();
    private final FakeOperationRepository operations = new FakeOperationRepository();
    private CatalogAdminSmartSaveService service;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(connection);
        service = new CatalogAdminSmartSaveService(
                dataSource, versions, journal, writer, locks, operations, gson, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void updatesOneEntityWithoutLoadingTheFullDraftAndPersistsReplayMetadata() {
        CatalogPageSnapshot edited = page(17, "Edited");

        CatalogSmartSaveResult result = service.apply(updateRequest("save-page-1", edited), draft -> {
            assertEquals(
                    "Original",
                    draft.page(CatalogPageType.NORMAL, 17).orElseThrow().caption());
        });

        assertEquals(5, result.revision());
        assertEquals(17, result.entityId());
        assertEquals(
                "Edited",
                gson.fromJson(result.entityJson(), CatalogPageSnapshot.class).caption());
        assertFalse(result.replayed());
        assertEquals(1, writer.applyCount);
        assertEquals(1, operations.insertCount);
        assertEquals("save-page-1", operations.record.operationId());
        assertEquals(11, result.historyGroup().id());
        assertEquals(0, versions.snapshotLoadCount);
    }

    @Test
    void returnsTheStoredResultForAnIdenticalRetryWithoutWritingAgain() {
        CatalogSmartSaveRequest request = updateRequest("save-page-retry", page(17, "Edited"));
        CatalogSmartSaveResult first = service.apply(request, ignored -> {});

        CatalogSmartSaveResult replay = service.apply(request, ignored -> {});

        assertEquals(first.revision(), replay.revision());
        assertEquals(first.entityJson(), replay.entityJson());
        assertTrue(replay.replayed());
        assertEquals(1, writer.applyCount);
        assertEquals(1, operations.insertCount);
    }

    @Test
    void rejectsReuseOfAnOperationIdForDifferentContent() {
        service.apply(updateRequest("save-page-conflict", page(17, "First")), ignored -> {});

        assertThrows(
                IllegalArgumentException.class,
                () -> service.apply(updateRequest("save-page-conflict", page(17, "Different")), ignored -> {}));
        assertEquals(1, writer.applyCount);
    }

    @Test
    void rollsBackWhenTheOwnedLockIsMissing() throws Exception {
        locks.available = false;

        assertThrows(
                CatalogLockConflictException.class,
                () -> service.apply(updateRequest("save-page-no-lock", page(17, "Edited")), ignored -> {}));

        verify(connection).rollback();
        assertEquals(0, writer.applyCount);
        assertEquals(0, operations.insertCount);
    }

    @Test
    void rejectsAStaleRevisionBeforeApplyingTheEntity() {
        CatalogSmartSaveRequest request = new CatalogSmartSaveRequest(
                "save-page-stale",
                DRAFT_ID,
                3,
                ACTOR_ID,
                "Editor",
                new CatalogLockKey(CatalogEntityType.PAGE, 17),
                LOCK_TOKEN,
                "Edit page",
                CatalogEntityType.PAGE,
                17,
                CatalogChangeOperation.UPDATE,
                gson.toJson(page(17, "Edited")));

        assertThrows(CatalogConcurrentModificationException.class, () -> service.apply(request, ignored -> {}));
        assertEquals(0, writer.applyCount);
    }

    @Test
    void allocatesTheCommittedIdentityForCreates() {
        CatalogDraftPageData draftPage = new CatalogDraftPageData(
                -1,
                "new_page",
                "New page",
                "default_3x3",
                1,
                1,
                1,
                1,
                true,
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
        CatalogSmartSaveRequest request = new CatalogSmartSaveRequest(
                "create-page-1",
                DRAFT_ID,
                4,
                ACTOR_ID,
                "Editor",
                new CatalogLockKey(CatalogEntityType.PAGE, Integer.MAX_VALUE),
                LOCK_TOKEN,
                "Create page",
                CatalogEntityType.PAGE,
                0,
                CatalogChangeOperation.CREATE,
                gson.toJson(draftPage));

        CatalogSmartSaveResult result = service.apply(request, ignored -> {});

        assertEquals(18, result.entityId());
        assertEquals(
                18,
                gson.fromJson(result.entityJson(), CatalogPageSnapshot.class).pageId());
    }

    @Test
    void hydratesOfferDraftDataWithTheExistingIdentityAndItemIds() {
        CatalogDraftOfferData edited =
                new CatalogDraftOfferData("", 17, "Edited offer", 25, 0, 0, 1, 0, 2, -1, 0, "", true, false);
        CatalogSmartSaveRequest request = new CatalogSmartSaveRequest(
                "save-offer-1",
                DRAFT_ID,
                4,
                ACTOR_ID,
                "Editor",
                new CatalogLockKey(CatalogEntityType.OFFER, 42),
                LOCK_TOKEN,
                "Edit offer",
                CatalogEntityType.OFFER,
                42,
                CatalogChangeOperation.UPDATE,
                gson.toJson(edited));

        CatalogSmartSaveResult result = service.apply(request, ignored -> {});
        CatalogOfferSnapshot committed = gson.fromJson(result.entityJson(), CatalogOfferSnapshot.class);

        assertEquals(42, committed.offerId());
        assertEquals("12", committed.itemIds());
        assertEquals("Edited offer", committed.catalogName());
    }

    private CatalogSmartSaveRequest updateRequest(String operationId, CatalogPageSnapshot page) {
        return new CatalogSmartSaveRequest(
                operationId,
                DRAFT_ID,
                4,
                ACTOR_ID,
                "Editor",
                new CatalogLockKey(CatalogEntityType.PAGE, 17),
                LOCK_TOKEN,
                "Edit page",
                CatalogEntityType.PAGE,
                17,
                CatalogChangeOperation.UPDATE,
                gson.toJson(page));
    }

    private static CatalogPageSnapshot page(int id, String caption) {
        return new CatalogPageSnapshot(
                id,
                -1,
                "page_" + id,
                caption,
                "default_3x3",
                1,
                1,
                1,
                1,
                true,
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

    private static CatalogVersion version(long revision) {
        return new CatalogVersion(
                DRAFT_ID,
                CatalogVersionStatus.DRAFT,
                1L,
                revision,
                "Shared draft",
                ACTOR_ID,
                NOW.minusSeconds(60),
                null,
                null);
    }

    private static final class FakeVersionRepository implements CatalogVersionRepository {
        private int snapshotLoadCount;

        @Override
        public CatalogRuntimeState lockRuntimeState(Connection connection) {
            return new CatalogRuntimeState(1, DRAFT_ID, NOW);
        }

        @Override
        public CatalogVersionSnapshot loadSnapshot(Connection connection, long versionId) {
            snapshotLoadCount++;
            throw new AssertionError("Smart Save must not load the full catalog snapshot");
        }

        @Override
        public CatalogVersion loadVersion(Connection connection, long versionId) {
            return version(4);
        }

        @Override
        public Optional<CatalogPageSnapshot> loadPage(
                Connection connection, long versionId, CatalogPageType catalogType, int pageId) {
            return pageId == 17 ? Optional.of(page(17, "Original")) : Optional.empty();
        }

        @Override
        public Optional<CatalogOfferSnapshot> loadOffer(
                Connection connection, long versionId, CatalogPageType catalogType, int offerId) {
            return offerId == 42
                    ? Optional.of(new CatalogOfferSnapshot(
                            42, "12", 17, "Original offer", 25, 0, 0, 1, 0, 1, -1, 0, "", true, false))
                    : Optional.empty();
        }

        @Override
        public long nextPageId(Connection connection) {
            return 18;
        }

        @Override
        public long nextOfferId(Connection connection) {
            return 44;
        }

        @Override
        public long incrementRevision(Connection connection, long versionId, long expectedRevision) {
            return expectedRevision + 1;
        }

        @Override
        public long cloneAsDraft(Connection connection, long sourceVersionId, int actorId, String label) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void archiveVersion(Connection connection, long versionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markPublished(Connection connection, long versionId, int actorId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateRuntimePointers(Connection connection, long activeVersionId, long draftVersionId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeJournal implements CatalogChangeJournal {
        private CatalogChangeGroup group;

        @Override
        public long append(
                Connection connection,
                long versionId,
                long revision,
                int actorId,
                String summary,
                CatalogChangeSource source,
                List<CatalogChangeEntry> entries) {
            group = new CatalogChangeGroup(11, versionId, revision, actorId, summary, source, NOW, entries);
            return group.id();
        }

        @Override
        public CatalogChangeGroup load(Connection connection, long groupId) {
            return group;
        }

        @Override
        public boolean hasLaterChangesToSameEntities(Connection connection, CatalogChangeGroup group) {
            return false;
        }
    }

    private static final class FakeWriter implements CatalogSnapshotWriter {
        private int applyCount;

        @Override
        public void apply(Connection connection, long versionId, CatalogChangeEntry change) {
            applyCount++;
        }

        @Override
        public void replace(Connection connection, long versionId, CatalogVersionSnapshot source) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeLockRepository implements CatalogLockRepository {
        private boolean available = true;

        @Override
        public Optional<CatalogLockRecord> findActive(
                Connection connection, long versionId, CatalogLockKey key, Instant now) {
            return available
                    ? Optional.of(new CatalogLockRecord(versionId, key, ACTOR_ID, LOCK_TOKEN, now.plusSeconds(60)))
                    : Optional.empty();
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

    private static final class FakeOperationRepository implements CatalogOperationRepository {
        private CatalogOperationRecord record;
        private int insertCount;

        @Override
        public Optional<CatalogOperationRecord> findForUpdate(Connection connection, String operationId, int actorId) {
            return Optional.ofNullable(record)
                    .filter(value -> value.operationId().equals(operationId) && value.actorId() == actorId);
        }

        @Override
        public void insert(Connection connection, CatalogOperationRecord record) {
            this.record = record;
            insertCount++;
        }
    }
}
