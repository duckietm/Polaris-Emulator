package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public final class CatalogLiveReconciliationService implements CatalogLiveReconciler {
    private final CatalogLiveSnapshotRepository liveSnapshots;
    private final CatalogSnapshotThreeWayMerge merger;
    private final CatalogVersionRepository versions;
    private final CatalogSnapshotWriter writer;
    private final CatalogChangeJournal journal;

    public CatalogLiveReconciliationService(
            CatalogLiveSnapshotRepository liveSnapshots,
            CatalogSnapshotThreeWayMerge merger,
            CatalogVersionRepository versions,
            CatalogSnapshotWriter writer,
            CatalogChangeJournal journal) {
        this.liveSnapshots = Objects.requireNonNull(liveSnapshots, "liveSnapshots");
        this.merger = Objects.requireNonNull(merger, "merger");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    @Override
    public CatalogLiveReconciliationResult reconcile(
            Connection connection, CatalogVersionSnapshot active, CatalogVersionSnapshot draft, int actorId)
            throws SQLException {
        CatalogVersionSnapshot live = liveSnapshots.load(connection, active.version());
        CatalogSnapshotMergeResult merge = merger.merge(active, live, draft);
        if (!merge.conflicts().isEmpty()) {
            return new CatalogLiveReconciliationResult(draft, 0, merge.conflicts());
        }
        List<CatalogChangeEntry> imported = merge.importedChanges();
        if (imported.isEmpty()) return new CatalogLiveReconciliationResult(draft, 0, List.of());

        writer.replace(connection, draft.version().id(), merge.snapshot());
        long revision = versions.incrementRevision(
                connection, draft.version().id(), draft.version().revision());
        CatalogVersionSnapshot reconciled = withRevision(merge.snapshot(), revision);
        journal.append(
                connection,
                draft.version().id(),
                revision,
                actorId,
                "Automatically imported live catalog changes",
                CatalogChangeSource.LIVE_SYNC,
                imported);
        return new CatalogLiveReconciliationResult(reconciled, imported.size(), List.of());
    }

    private static CatalogVersionSnapshot withRevision(CatalogVersionSnapshot snapshot, long revision) {
        CatalogVersion current = snapshot.version();
        CatalogVersion updated = new CatalogVersion(
                current.id(),
                current.status(),
                current.basedOnVersionId(),
                revision,
                current.label(),
                current.createdBy(),
                current.createdAt(),
                current.publishedBy(),
                current.publishedAt());
        return new CatalogVersionSnapshot(updated, snapshot.pages(), snapshot.offers());
    }
}
