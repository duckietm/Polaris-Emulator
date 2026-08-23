package com.eu.habbo.habbohotel.catalog.versioning;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/** Dry-runs and applies SQL/JSONC repairs directly to live with complete operation history. */
public final class CatalogLiveChangeSetService {
    private final DataSource dataSource;
    private final CatalogVersionRepository versions;
    private final CatalogLiveSnapshotRepository liveSnapshots;
    private final CatalogLiveEntityWriter live;
    private final CatalogChangeJournal journal;
    private final CatalogStudioDocumentService documents;
    private final CatalogOperationRepository operations;
    private final CatalogLiveMutationHook hook;
    private final CatalogLiveValidationGuard validation;

    public CatalogLiveChangeSetService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogSnapshotWriter snapshots,
            CatalogLiveEntityWriter live,
            CatalogChangeJournal journal,
            CatalogStudioDocumentService documents,
            CatalogOperationRepository operations,
            CatalogLiveMutationHook hook) {
        this(dataSource, versions, null, live, journal, documents, operations, hook, null);
    }

    public CatalogLiveChangeSetService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogSnapshotWriter snapshots,
            CatalogLiveSnapshotRepository liveSnapshots,
            CatalogLiveEntityWriter live,
            CatalogChangeJournal journal,
            CatalogStudioDocumentService documents,
            CatalogOperationRepository operations,
            CatalogLiveMutationHook hook,
            CatalogLiveValidationGuard validation) {
        this(dataSource, versions, liveSnapshots, live, journal, documents, operations, hook, validation);
    }

    public CatalogLiveChangeSetService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogLiveSnapshotRepository liveSnapshots,
            CatalogLiveEntityWriter live,
            CatalogChangeJournal journal,
            CatalogStudioDocumentService documents,
            CatalogOperationRepository operations,
            CatalogLiveMutationHook hook,
            CatalogLiveValidationGuard validation) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.liveSnapshots = liveSnapshots;
        this.live = Objects.requireNonNull(live, "live");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.documents = Objects.requireNonNull(documents, "documents");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.hook = Objects.requireNonNull(hook, "hook");
        this.validation = validation;
    }

    public CatalogImportDryRun dryRun(String format, String document) {
        try (Connection connection = dataSource.getConnection()) {
            CatalogRuntimeState state = versions.lockRuntimeState(connection);
            CatalogVersionSnapshot active = loadPhysicalLive(connection, state);
            verifyActive(active, state.activeVersionId());
            return documents.dryRun(active, format, document);
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Live catalog change-set dry-run failed", exception);
        }
    }

    public CatalogChangeSetApplyResult apply(
            String operationId, int actorId, String format, String document, String fingerprint, String summary) {
        CatalogOperationRecord.validateIdentity(operationId, actorId);
        List<CatalogChangeEntry> committedChanges;
        CatalogChangeSetApplyResult result;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Long> replay = operations
                        .findForUpdate(connection, operationId, actorId)
                        .map(CatalogOperationRecord::resultRevision);
                if (replay.isPresent()) {
                    connection.commit();
                    return new CatalogChangeSetApplyResult(replay.get(), 0, true);
                }
                CatalogRuntimeState state = versions.lockRuntimeState(connection);
                CatalogVersionSnapshot active = loadPhysicalLive(connection, state);
                verifyActive(active, state.activeVersionId());
                CatalogImportDryRun dryRun = documents.dryRun(active, format, document);
                if (!MessageDigest.isEqual(
                        dryRun.fingerprint().getBytes(StandardCharsets.US_ASCII),
                        Objects.requireNonNull(fingerprint, "fingerprint").getBytes(StandardCharsets.US_ASCII))) {
                    throw new CatalogConcurrentModificationException(
                            active.version().id(), active.version().revision());
                }
                committedChanges = dryRun.changes();
                if (validation != null) validation.rejectIntroducedProblems(connection, active, committedChanges);
                for (CatalogChangeEntry change : committedChanges) {
                    live.apply(connection, change);
                }
                long revision = versions.incrementRevision(
                        connection, active.version().id(), active.version().revision());
                journal.append(
                        connection,
                        active.version().id(),
                        revision,
                        actorId,
                        summary,
                        dryRun.source(),
                        committedChanges);
                operations.insert(
                        connection,
                        new CatalogOperationRecord(
                                operationId,
                                actorId,
                                active.version().id(),
                                dryRun.source(),
                                revision,
                                fingerprint,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));
                connection.commit();
                result = new CatalogChangeSetApplyResult(revision, committedChanges.size(), false);
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
            throw new CatalogVersioningException("Live catalog change-set apply failed", exception);
        }
        committedChanges.forEach(hook::afterCommit);
        return result;
    }

    private static void verifyActive(CatalogVersionSnapshot active, long activeVersionId) {
        if (active.version().status() != CatalogVersionStatus.PUBLISHED
                || active.version().id() != activeVersionId) {
            throw new IllegalStateException("Live catalog state not found");
        }
    }

    private CatalogVersionSnapshot loadPhysicalLive(Connection connection, CatalogRuntimeState state)
            throws SQLException {
        CatalogVersion version = versions.loadVersion(connection, state.activeVersionId());
        return liveSnapshots == null
                ? versions.loadSnapshot(connection, state.activeVersionId())
                : liveSnapshots.load(connection, version);
    }
}
