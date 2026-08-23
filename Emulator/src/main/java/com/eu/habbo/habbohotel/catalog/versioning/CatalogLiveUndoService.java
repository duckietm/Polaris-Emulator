package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** Reverses one complete live operation while preserving both audit records. */
public final class CatalogLiveUndoService {
    private final DataSource dataSource;
    private final CatalogVersionRepository versions;
    private final CatalogChangeJournal journal;
    private final CatalogLiveSnapshotRepository liveSnapshots;
    private final CatalogLiveEntityWriter live;
    private final CatalogLiveMutationHook hook;
    private final CatalogOperationRepository operations;

    public CatalogLiveUndoService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogSnapshotWriter snapshots,
            CatalogLiveEntityWriter live,
            CatalogLiveMutationHook hook) {
        this(dataSource, versions, journal, null, live, hook, null);
    }

    public CatalogLiveUndoService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogSnapshotWriter snapshots,
            CatalogLiveSnapshotRepository liveSnapshots,
            CatalogLiveEntityWriter live,
            CatalogLiveMutationHook hook) {
        this(dataSource, versions, journal, liveSnapshots, live, hook, null);
    }

    public CatalogLiveUndoService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogLiveSnapshotRepository liveSnapshots,
            CatalogLiveEntityWriter live,
            CatalogLiveMutationHook hook) {
        this(dataSource, versions, journal, liveSnapshots, live, hook, null);
    }

    public CatalogLiveUndoService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogLiveSnapshotRepository liveSnapshots,
            CatalogLiveEntityWriter live,
            CatalogLiveMutationHook hook,
            CatalogOperationRepository operations) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.liveSnapshots = liveSnapshots;
        this.live = Objects.requireNonNull(live, "live");
        this.hook = Objects.requireNonNull(hook, "hook");
        this.operations = operations;
    }

    public long undo(long groupId, int actorId) {
        return undo(groupId, actorId, -1, "");
    }

    public long undo(long groupId, int actorId, long expectedRevision, String operationId) {
        if (!operationId.isEmpty()) CatalogOperationRecord.validateIdentity(operationId, actorId);
        List<CatalogChangeEntry> inverse;
        long revision;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CatalogRuntimeState state = versions.lockRuntimeState(connection);
                if (operations != null && !operationId.isEmpty()) {
                    var replay = operations.findForUpdate(connection, operationId, actorId);
                    if (replay.isPresent()) {
                        connection.commit();
                        return replay.get().resultRevision();
                    }
                }
                CatalogVersion version = versions.loadVersion(connection, state.activeVersionId());
                CatalogVersionSnapshot active = liveSnapshots == null
                        ? versions.loadSnapshot(connection, state.activeVersionId())
                        : liveSnapshots.load(connection, version);
                if (active.version().status() != CatalogVersionStatus.PUBLISHED) {
                    throw new IllegalStateException("Live catalog state is not available");
                }
                if (expectedRevision >= 0 && active.version().revision() != expectedRevision) {
                    throw new CatalogConcurrentModificationException(
                            active.version().id(), expectedRevision);
                }
                CatalogChangeGroup group = journal.load(connection, groupId);
                if (group.versionId() != active.version().id()) {
                    throw new CatalogUndoConflictException("The selected change belongs to another catalog version");
                }
                if (journal.hasLaterChangesToSameEntities(connection, group)) {
                    throw new CatalogUndoConflictException("One or more entities were edited after this operation");
                }
                inverse = inverse(group.entries());
                for (CatalogChangeEntry change : inverse) {
                    live.apply(connection, change);
                }
                revision = versions.incrementRevision(
                        connection, active.version().id(), active.version().revision());
                long undoHistoryId = journal.append(
                        connection,
                        active.version().id(),
                        revision,
                        actorId,
                        "Undo operation #" + groupId,
                        CatalogChangeSource.UNDO,
                        inverse);
                if (operations != null && !operationId.isEmpty()) {
                    operations.insert(
                            connection,
                            new CatalogOperationRecord(
                                    operationId,
                                    actorId,
                                    active.version().id(),
                                    CatalogChangeSource.UNDO,
                                    revision,
                                    "undo:" + groupId,
                                    null,
                                    null,
                                    null,
                                    null,
                                    undoHistoryId,
                                    null));
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Live catalog undo failed", exception);
        }
        inverse.forEach(hook::afterCommit);
        return revision;
    }

    private static List<CatalogChangeEntry> inverse(List<CatalogChangeEntry> entries) {
        List<CatalogChangeEntry> result = new ArrayList<>(entries.size());
        for (int index = entries.size() - 1; index >= 0; index--) {
            CatalogChangeEntry entry = entries.get(index);
            CatalogChangeOperation operation =
                    switch (entry.operation()) {
                        case CREATE -> CatalogChangeOperation.DELETE;
                        case DELETE -> CatalogChangeOperation.CREATE;
                        case UPDATE -> CatalogChangeOperation.UPDATE;
                        case MOVE -> CatalogChangeOperation.MOVE;
                    };
            result.add(new CatalogChangeEntry(
                    0,
                    entry.entityType(),
                    entry.catalogType(),
                    entry.entityId(),
                    operation,
                    entry.afterJson(),
                    entry.beforeJson()));
        }
        return List.copyOf(result);
    }
}
