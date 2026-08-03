package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogAdminDraftMutationServiceTest {
    private static final long DRAFT_ID = 2;
    private static final int ACTOR_ID = 7;

    private final Gson gson = new Gson();
    private final FakeVersionRepository versions = new FakeVersionRepository();
    private final FakeJournal journal = new FakeJournal();
    private final FakeWriter writer = new FakeWriter(gson, versions);
    private final FakeLockRepository locks = new FakeLockRepository();
    private CatalogAdminDraftMutationService service;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(mock(Connection.class));
        versions.snapshot = snapshot(4, List.of(page(17, "Original")), List.of(offer(42, 1), offer(43, 2)));
        versions.runtimeState = new CatalogRuntimeState(1, DRAFT_ID, Instant.parse("2026-08-02T11:00:00Z"));
        service = new CatalogAdminDraftMutationService(
                dataSource,
                versions,
                journal,
                writer,
                locks,
                gson,
                Clock.fixed(Instant.parse("2026-08-02T11:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void updatesOnlyTheDraftAndAppendsExactBeforeAndAfterJson() {
        UUID token = locks.own(new CatalogLockKey(CatalogEntityType.PAGE, 17), ACTOR_ID);
        CatalogPageSnapshot edited = page(17, "Edited");
        CatalogDraftMutationRequest request = request(
                new CatalogLockKey(CatalogEntityType.PAGE, 17),
                token,
                CatalogEntityType.PAGE,
                17,
                CatalogChangeOperation.UPDATE,
                gson.toJson(edited));

        CatalogDraftMutationResult result = service.apply(request);

        assertEquals(5, result.revision());
        assertEquals("Edited", versions.snapshot.page(17).orElseThrow().caption());
        assertEquals(1, journal.groups.size());
        CatalogChangeEntry entry = journal.groups.getFirst().entries().getFirst();
        assertEquals(gson.toJson(page(17, "Original")), entry.beforeJson());
        assertEquals(gson.toJson(edited), entry.afterJson());
    }

    @Test
    void rejectsAMutationWithoutTheOwnedActiveLock() {
        UUID token = locks.own(new CatalogLockKey(CatalogEntityType.PAGE, 17), ACTOR_ID);
        CatalogDraftMutationRequest request = request(
                new CatalogLockKey(CatalogEntityType.PAGE, 17),
                new UUID(token.getMostSignificantBits(), token.getLeastSignificantBits() + 1),
                CatalogEntityType.PAGE,
                17,
                CatalogChangeOperation.UPDATE,
                gson.toJson(page(17, "Edited")));

        assertThrows(CatalogLockConflictException.class, () -> service.apply(request));
        assertEquals("Original", versions.snapshot.page(17).orElseThrow().caption());
        assertTrue(journal.groups.isEmpty());
    }

    @Test
    void rejectsAStaleRevisionBeforeApplyingAnyChange() {
        UUID token = locks.own(new CatalogLockKey(CatalogEntityType.PAGE, 17), ACTOR_ID);
        CatalogDraftMutationRequest request = new CatalogDraftMutationRequest(
                DRAFT_ID,
                3,
                ACTOR_ID,
                new CatalogLockKey(CatalogEntityType.PAGE, 17),
                token,
                "Edit page",
                CatalogEntityType.PAGE,
                17,
                CatalogChangeOperation.UPDATE,
                gson.toJson(page(17, "Edited")));

        assertThrows(CatalogConcurrentModificationException.class, () -> service.apply(request));
        assertEquals("Original", versions.snapshot.page(17).orElseThrow().caption());
    }

    @Test
    void loadsTheActiveDraftSnapshotOnlyAtTheExpectedRevision() {
        CatalogVersionSnapshot loaded = service.loadDraft(DRAFT_ID, 4);

        assertEquals("Original", loaded.page(17).orElseThrow().caption());
        assertThrows(CatalogConcurrentModificationException.class, () -> service.loadDraft(DRAFT_ID, 3));
    }

    @Test
    void createsAPageWithANewStableIdWhileHoldingTheParentLock() {
        UUID token = locks.own(new CatalogLockKey(CatalogEntityType.PAGE, 17), ACTOR_ID);
        CatalogDraftPageData pageData = new CatalogDraftPageData(
                17,
                "new_page",
                "New page",
                "default_3x3",
                1,
                2,
                1,
                3,
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
        CatalogDraftMutationRequest request = request(
                new CatalogLockKey(CatalogEntityType.PAGE, 17),
                token,
                CatalogEntityType.PAGE,
                0,
                CatalogChangeOperation.CREATE,
                gson.toJson(pageData));

        CatalogDraftMutationResult result = service.apply(request);

        assertEquals(18, result.entityId());
        assertEquals("New page", versions.snapshot.page(18).orElseThrow().caption());
        assertEquals(CatalogChangeOperation.CREATE, result.change().operation());
        assertEquals(null, result.change().beforeJson());
    }

    @Test
    void deletesAnOfferFromTheDraftWithoutTouchingOtherOffers() {
        UUID token = locks.own(new CatalogLockKey(CatalogEntityType.OFFER, 42), ACTOR_ID);
        CatalogDraftMutationRequest request = request(
                new CatalogLockKey(CatalogEntityType.OFFER, 42),
                token,
                CatalogEntityType.OFFER,
                42,
                CatalogChangeOperation.DELETE,
                null);

        CatalogDraftMutationResult result = service.apply(request);

        assertTrue(versions.snapshot.offer(42).isEmpty());
        assertTrue(versions.snapshot.offer(43).isPresent());
        assertEquals(CatalogChangeOperation.DELETE, result.change().operation());
    }

    @Test
    void appliesAReorderBatchAsOneRevisionAndOneJournalGroup() {
        UUID firstToken = locks.own(new CatalogLockKey(CatalogEntityType.OFFER, 42), ACTOR_ID);
        UUID secondToken = locks.own(new CatalogLockKey(CatalogEntityType.OFFER, 43), ACTOR_ID);
        CatalogDraftMutationRequest first = request(
                new CatalogLockKey(CatalogEntityType.OFFER, 42),
                firstToken,
                CatalogEntityType.OFFER,
                42,
                CatalogChangeOperation.MOVE,
                gson.toJson(offer(42, 2)));
        CatalogDraftMutationRequest second = request(
                new CatalogLockKey(CatalogEntityType.OFFER, 43),
                secondToken,
                CatalogEntityType.OFFER,
                43,
                CatalogChangeOperation.MOVE,
                gson.toJson(offer(43, 1)));

        CatalogDraftMutationBatchResult result = service.applyBatch(List.of(first, second));

        assertEquals(5, result.revision());
        assertEquals(2, result.changes().size());
        assertEquals(1, journal.groups.size());
        assertEquals(2, versions.snapshot.offer(42).orElseThrow().orderNumber());
        assertEquals(1, versions.snapshot.offer(43).orElseThrow().orderNumber());
    }

    private CatalogDraftMutationRequest request(
            CatalogLockKey lockKey,
            UUID token,
            CatalogEntityType entityType,
            int entityId,
            CatalogChangeOperation operation,
            String afterJson) {
        return new CatalogDraftMutationRequest(
                DRAFT_ID,
                4,
                ACTOR_ID,
                lockKey,
                token,
                operation == CatalogChangeOperation.MOVE ? "Reorder offers" : "Edit catalog",
                entityType,
                entityId,
                operation,
                afterJson);
    }

    private static CatalogVersionSnapshot snapshot(
            long revision, List<CatalogPageSnapshot> pages, List<CatalogOfferSnapshot> offers) {
        return new CatalogVersionSnapshot(
                new CatalogVersion(
                        DRAFT_ID,
                        CatalogVersionStatus.DRAFT,
                        1L,
                        revision,
                        "Shared draft",
                        ACTOR_ID,
                        Instant.parse("2026-08-02T10:00:00Z"),
                        null,
                        null),
                pages,
                offers);
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

    private static CatalogOfferSnapshot offer(int id, int order) {
        return new CatalogOfferSnapshot(id, "12", 17, "offer_" + id, 25, 0, 0, 1, 0, order, -1, 0, "", true, false);
    }

    private static final class FakeVersionRepository implements CatalogVersionRepository {
        private CatalogRuntimeState runtimeState;
        private CatalogVersionSnapshot snapshot;

        @Override
        public CatalogRuntimeState lockRuntimeState(Connection connection) {
            return runtimeState;
        }

        @Override
        public CatalogVersionSnapshot loadSnapshot(Connection connection, long versionId) {
            return snapshot;
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
            if (snapshot.version().revision() != expectedRevision) {
                throw new CatalogConcurrentModificationException(versionId, expectedRevision);
            }
            long next = expectedRevision + 1;
            CatalogVersion version = snapshot.version();
            snapshot = new CatalogVersionSnapshot(
                    new CatalogVersion(
                            version.id(),
                            version.status(),
                            version.basedOnVersionId(),
                            next,
                            version.label(),
                            version.createdBy(),
                            version.createdAt(),
                            version.publishedBy(),
                            version.publishedAt()),
                    snapshot.pages(),
                    snapshot.offers());
            return next;
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

    private static final class FakeLockRepository implements CatalogLockRepository {
        private final Map<CatalogLockKey, CatalogLockRecord> records = new HashMap<>();

        UUID own(CatalogLockKey key, int ownerId) {
            UUID token = UUID.randomUUID();
            records.put(
                    key, new CatalogLockRecord(DRAFT_ID, key, ownerId, token, Instant.parse("2026-08-02T12:00:00Z")));
            return token;
        }

        @Override
        public Optional<CatalogLockRecord> findActive(
                Connection connection, long versionId, CatalogLockKey key, Instant now) {
            return Optional.ofNullable(records.get(key))
                    .filter(record -> record.expiresAt().isAfter(now));
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

    private static final class FakeJournal implements CatalogChangeJournal {
        private final List<CatalogChangeGroup> groups = new ArrayList<>();

        @Override
        public long append(
                Connection connection,
                long versionId,
                long revision,
                int actorId,
                String summary,
                CatalogChangeSource source,
                List<CatalogChangeEntry> entries) {
            groups.add(new CatalogChangeGroup(
                    groups.size() + 1L,
                    versionId,
                    revision,
                    actorId,
                    summary,
                    source,
                    Instant.parse("2026-08-02T11:01:00Z"),
                    entries));
            return groups.size();
        }

        @Override
        public CatalogChangeGroup load(Connection connection, long groupId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasLaterChangesToSameEntities(Connection connection, CatalogChangeGroup group) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeWriter implements CatalogSnapshotWriter {
        private final Gson gson;
        private final FakeVersionRepository versions;

        private FakeWriter(Gson gson, FakeVersionRepository versions) {
            this.gson = gson;
            this.versions = versions;
        }

        @Override
        public void apply(Connection connection, long versionId, CatalogChangeEntry change) {
            List<CatalogPageSnapshot> pages = new ArrayList<>(versions.snapshot.pages());
            List<CatalogOfferSnapshot> offers = new ArrayList<>(versions.snapshot.offers());
            if (change.entityType() == CatalogEntityType.PAGE) {
                pages.removeIf(page -> page.pageId() == change.entityId());
                if (change.afterJson() != null) {
                    pages.add(gson.fromJson(change.afterJson(), CatalogPageSnapshot.class));
                }
            } else {
                offers.removeIf(offer -> offer.offerId() == change.entityId());
                if (change.afterJson() != null) {
                    offers.add(gson.fromJson(change.afterJson(), CatalogOfferSnapshot.class));
                }
            }
            versions.snapshot = new CatalogVersionSnapshot(versions.snapshot.version(), pages, offers);
        }

        @Override
        public void replace(Connection connection, long versionId, CatalogVersionSnapshot source) {
            throw new UnsupportedOperationException();
        }
    }
}
