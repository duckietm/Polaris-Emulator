package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatalogUndoServiceTest {
    private final Gson gson = new Gson();
    private final FakeVersionRepository versions = new FakeVersionRepository();
    private final FakeChangeJournal journal = new FakeChangeJournal();
    private final FakeSnapshotWriter writer = new FakeSnapshotWriter(gson, versions);
    private CatalogUndoService service;

    @BeforeEach
    void setUp() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(mock(Connection.class));
        service = new CatalogUndoService(dataSource, versions, journal, writer, gson);

        versions.put(snapshot(1, CatalogVersionStatus.PUBLISHED, 0, page(17, "Published")));
        versions.put(snapshot(2, CatalogVersionStatus.DRAFT, 4, page(17, "Edited")));
        versions.runtimeState = new CatalogRuntimeState(1, 2, Instant.parse("2026-08-02T10:00:00Z"));
    }

    @Test
    void undoAppliesTheInverseAndAppendsAnUndoGroupWithoutDeletingHistory() {
        CatalogPageSnapshot before = page(17, "Published");
        CatalogPageSnapshot after = page(17, "Edited");
        CatalogChangeGroup original = journal.addExisting(
                2,
                4,
                CatalogChangeSource.UI,
                List.of(new CatalogChangeEntry(
                        1,
                        CatalogEntityType.PAGE,
                        17,
                        CatalogChangeOperation.UPDATE,
                        gson.toJson(before),
                        gson.toJson(after))));

        long revision = service.undo(2, 4, original.id(), 9);

        assertEquals(5, revision);
        assertEquals(
                "Published", versions.snapshots.get(2L).page(17).orElseThrow().caption());
        assertEquals(2, journal.groups.size());
        CatalogChangeGroup undo = journal.groups.get(1);
        assertEquals(CatalogChangeSource.UNDO, undo.source());
        assertEquals(gson.toJson(after), undo.entries().getFirst().beforeJson());
        assertEquals(gson.toJson(before), undo.entries().getFirst().afterJson());
    }

    @Test
    void undoRejectsAGroupFromAnotherDraft() {
        CatalogChangeGroup foreign = journal.addExisting(
                99,
                1,
                CatalogChangeSource.UI,
                List.of(new CatalogChangeEntry(
                        1,
                        CatalogEntityType.PAGE,
                        17,
                        CatalogChangeOperation.UPDATE,
                        gson.toJson(page(17, "A")),
                        gson.toJson(page(17, "B")))));

        assertThrows(CatalogUndoConflictException.class, () -> service.undo(2, 4, foreign.id(), 9));
        assertEquals("Edited", versions.snapshots.get(2L).page(17).orElseThrow().caption());
    }

    @Test
    void undoRejectsAnEntityChangedAgainAfterTheSelectedGroup() {
        CatalogChangeGroup original = journal.addExisting(
                2,
                3,
                CatalogChangeSource.UI,
                List.of(new CatalogChangeEntry(
                        1,
                        CatalogEntityType.PAGE,
                        17,
                        CatalogChangeOperation.UPDATE,
                        gson.toJson(page(17, "Published")),
                        gson.toJson(page(17, "First edit")))));
        journal.hasLaterConflict = true;

        assertThrows(CatalogUndoConflictException.class, () -> service.undo(2, 4, original.id(), 9));
        assertEquals(1, journal.groups.size());
    }

    @Test
    void undoRejectsAStaleExpectedRevision() {
        CatalogChangeGroup original = journal.addExisting(
                2,
                4,
                CatalogChangeSource.UI,
                List.of(new CatalogChangeEntry(
                        1,
                        CatalogEntityType.PAGE,
                        17,
                        CatalogChangeOperation.UPDATE,
                        gson.toJson(page(17, "Published")),
                        gson.toJson(page(17, "Edited")))));

        assertThrows(CatalogConcurrentModificationException.class, () -> service.undo(2, 3, original.id(), 9));
        assertEquals(1, journal.groups.size());
    }

    @Test
    void restoreReplacesTheDraftAndRecordsOneRestoreGroup() {
        long revision = service.restore(2, 4, 1, 9);

        assertEquals(5, revision);
        assertEquals(
                "Published", versions.snapshots.get(2L).page(17).orElseThrow().caption());
        assertEquals(1, journal.groups.size());
        CatalogChangeGroup restore = journal.groups.getFirst();
        assertEquals(CatalogChangeSource.RESTORE, restore.source());
        assertTrue(restore.entries().stream()
                .anyMatch(entry -> entry.entityType() == CatalogEntityType.PAGE
                        && entry.entityId() == 17
                        && entry.operation() == CatalogChangeOperation.UPDATE));
    }

    private static CatalogVersionSnapshot snapshot(
            long id, CatalogVersionStatus status, long revision, CatalogPageSnapshot page) {
        return new CatalogVersionSnapshot(
                new CatalogVersion(
                        id,
                        status,
                        status == CatalogVersionStatus.DRAFT ? 1L : null,
                        revision,
                        status.name(),
                        7,
                        Instant.parse("2026-08-02T09:00:00Z"),
                        status == CatalogVersionStatus.PUBLISHED ? 7 : null,
                        status == CatalogVersionStatus.PUBLISHED ? Instant.parse("2026-08-02T09:30:00Z") : null),
                List.of(page),
                List.of());
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

    private static final class FakeVersionRepository implements CatalogVersionRepository {
        private final Map<Long, CatalogVersionSnapshot> snapshots = new HashMap<>();
        private CatalogRuntimeState runtimeState;

        void put(CatalogVersionSnapshot snapshot) {
            snapshots.put(snapshot.version().id(), snapshot);
        }

        @Override
        public CatalogRuntimeState lockRuntimeState(Connection connection) {
            return runtimeState;
        }

        @Override
        public CatalogVersionSnapshot loadSnapshot(Connection connection, long versionId) {
            return snapshots.get(versionId);
        }

        @Override
        public long incrementRevision(Connection connection, long versionId, long expectedRevision) {
            CatalogVersionSnapshot current = snapshots.get(versionId);
            if (current.version().revision() != expectedRevision) {
                throw new CatalogConcurrentModificationException(versionId, expectedRevision);
            }
            long nextRevision = expectedRevision + 1;
            CatalogVersion version = current.version();
            snapshots.put(
                    versionId,
                    new CatalogVersionSnapshot(
                            new CatalogVersion(
                                    version.id(),
                                    version.status(),
                                    version.basedOnVersionId(),
                                    nextRevision,
                                    version.label(),
                                    version.createdBy(),
                                    version.createdAt(),
                                    version.publishedBy(),
                                    version.publishedAt()),
                            current.pages(),
                            current.offers()));
            return nextRevision;
        }

        @Override
        public long cloneAsDraft(Connection connection, long sourceVersionId, int actorId, String label) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long nextPageId(Connection connection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long nextOfferId(Connection connection) {
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

    private static final class FakeChangeJournal implements CatalogChangeJournal {
        private final List<CatalogChangeGroup> groups = new ArrayList<>();
        private boolean hasLaterConflict;

        CatalogChangeGroup addExisting(
                long versionId, long revision, CatalogChangeSource source, List<CatalogChangeEntry> entries) {
            CatalogChangeGroup group = new CatalogChangeGroup(
                    groups.size() + 1L,
                    versionId,
                    revision,
                    7,
                    "Existing change",
                    source,
                    Instant.parse("2026-08-02T10:00:00Z"),
                    entries);
            groups.add(group);
            return group;
        }

        @Override
        public CatalogChangeGroup load(Connection connection, long groupId) {
            return groups.stream()
                    .filter(group -> group.id() == groupId)
                    .findFirst()
                    .orElseThrow();
        }

        @Override
        public boolean hasLaterChangesToSameEntities(Connection connection, CatalogChangeGroup group) {
            return hasLaterConflict;
        }

        @Override
        public long append(
                Connection connection,
                long versionId,
                long revision,
                int actorId,
                String summary,
                CatalogChangeSource source,
                List<CatalogChangeEntry> entries) {
            CatalogChangeGroup group = new CatalogChangeGroup(
                    groups.size() + 1L,
                    versionId,
                    revision,
                    actorId,
                    summary,
                    source,
                    Instant.parse("2026-08-02T10:01:00Z"),
                    entries);
            groups.add(group);
            return group.id();
        }
    }

    private static final class FakeSnapshotWriter implements CatalogSnapshotWriter {
        private final Gson gson;
        private final FakeVersionRepository versions;

        private FakeSnapshotWriter(Gson gson, FakeVersionRepository versions) {
            this.gson = gson;
            this.versions = versions;
        }

        @Override
        public void apply(Connection connection, long versionId, CatalogChangeEntry change) {
            CatalogVersionSnapshot current = versions.snapshots.get(versionId);
            List<CatalogPageSnapshot> pages = new ArrayList<>(current.pages());
            if (change.entityType() == CatalogEntityType.PAGE) {
                pages.removeIf(page -> page.pageId() == change.entityId());
                if (change.afterJson() != null) {
                    pages.add(gson.fromJson(change.afterJson(), CatalogPageSnapshot.class));
                }
            }
            versions.snapshots.put(versionId, new CatalogVersionSnapshot(current.version(), pages, current.offers()));
        }

        @Override
        public void replace(Connection connection, long versionId, CatalogVersionSnapshot source) {
            CatalogVersionSnapshot current = versions.snapshots.get(versionId);
            versions.snapshots.put(
                    versionId, new CatalogVersionSnapshot(current.version(), source.pages(), source.offers()));
        }
    }
}
