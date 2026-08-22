package com.eu.habbo.habbohotel.catalog.versioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogLiveReconciliationServiceTest {
    private final Connection connection = org.mockito.Mockito.mock(Connection.class);

    @Test
    void persistsLiveChangesInTheDraftAndJournalsTheNewRevision() throws Exception {
        CatalogVersionSnapshot active = snapshot(1, CatalogVersionStatus.PUBLISHED, 10, 0);
        CatalogVersionSnapshot draft = snapshot(2, CatalogVersionStatus.DRAFT, 10, 5);
        CatalogVersionSnapshot live = snapshot(1, CatalogVersionStatus.PUBLISHED, 25, 0);
        RecordingVersions versions = new RecordingVersions();
        RecordingWriter writer = new RecordingWriter();
        RecordingJournal journal = new RecordingJournal();
        CatalogLiveReconciliationService service = new CatalogLiveReconciliationService(
                (ignored, version) -> live, new CatalogSnapshotThreeWayMerge(new Gson()), versions, writer, journal);

        CatalogLiveReconciliationResult result = service.reconcile(connection, active, draft, 7);

        assertTrue(result.conflicts().isEmpty());
        assertEquals(1, result.importedChanges());
        assertEquals(6, result.snapshot().version().revision());
        assertEquals(25, result.snapshot().offer(10).orElseThrow().costCredits());
        assertEquals(2, writer.versionId);
        assertEquals(25, writer.snapshot.offer(10).orElseThrow().costCredits());
        assertEquals(5, versions.expectedRevision);
        assertEquals(CatalogChangeSource.LIVE_SYNC, journal.source);
        assertEquals(6, journal.revision);
        assertEquals(1, journal.entries.size());
    }

    private static CatalogVersionSnapshot snapshot(
            long versionId, CatalogVersionStatus status, int credits, long revision) {
        CatalogVersion version = new CatalogVersion(
                versionId,
                status,
                status == CatalogVersionStatus.DRAFT ? 1L : null,
                revision,
                status.name(),
                7,
                Instant.parse("2026-08-21T10:00:00Z"),
                status == CatalogVersionStatus.PUBLISHED ? 7 : null,
                status == CatalogVersionStatus.PUBLISHED ? Instant.parse("2026-08-21T10:00:00Z") : null);
        CatalogPageSnapshot page = new CatalogPageSnapshot(
                1, -1, "shop", "Shop", "root", 0, 0, 1, 0, true, true, false, "NORMAL", false, "", "", "", "", "", "",
                "", 0, "");
        CatalogOfferSnapshot offer =
                new CatalogOfferSnapshot(10, "100", 1, "Chair", credits, 0, 0, 1, 0, 0, 10, 0, "", true, false);
        return new CatalogVersionSnapshot(version, List.of(page), List.of(offer));
    }

    private static final class RecordingVersions implements CatalogVersionRepository {
        private long expectedRevision = -1;

        @Override
        public CatalogRuntimeState lockRuntimeState(Connection connection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CatalogVersionSnapshot loadSnapshot(Connection connection, long versionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long incrementRevision(Connection connection, long versionId, long expectedRevision) {
            this.expectedRevision = expectedRevision;
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

        @Override
        public long nextPageId(Connection connection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long nextOfferId(Connection connection) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingWriter implements CatalogSnapshotWriter {
        private long versionId;
        private CatalogVersionSnapshot snapshot;

        @Override
        public void apply(Connection connection, long versionId, CatalogChangeEntry change) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void replace(Connection connection, long versionId, CatalogVersionSnapshot source) {
            this.versionId = versionId;
            this.snapshot = source;
        }
    }

    private static final class RecordingJournal implements CatalogChangeJournal {
        private long revision;
        private CatalogChangeSource source;
        private List<CatalogChangeEntry> entries = List.of();

        @Override
        public CatalogChangeGroup load(Connection connection, long groupId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasLaterChangesToSameEntities(Connection connection, CatalogChangeGroup group) {
            throw new UnsupportedOperationException();
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
            this.revision = revision;
            this.source = source;
            this.entries = List.copyOf(entries);
            return 1;
        }
    }
}
