package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class CatalogDraftChangeSetService {
    public static final int ROOT_LOCK_ID = Integer.MAX_VALUE;

    private final DataSource dataSource;
    private final CatalogVersionRepository versions;
    private final CatalogSnapshotWriter writer;
    private final CatalogChangeJournal journal;
    private final CatalogLockRepository locks;
    private final CatalogStudioDocumentService documents;
    private final CatalogOperationRepository operations;
    private final Clock clock;

    public CatalogDraftChangeSetService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogSnapshotWriter writer,
            CatalogChangeJournal journal,
            CatalogLockRepository locks,
            CatalogStudioDocumentService documents) {
        this(
                dataSource,
                versions,
                writer,
                journal,
                locks,
                documents,
                new JdbcCatalogOperationRepository(),
                Clock.systemUTC());
    }

    CatalogDraftChangeSetService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogSnapshotWriter writer,
            CatalogChangeJournal journal,
            CatalogLockRepository locks,
            CatalogStudioDocumentService documents,
            Clock clock) {
        this(dataSource, versions, writer, journal, locks, documents, new JdbcCatalogOperationRepository(), clock);
    }

    CatalogDraftChangeSetService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogSnapshotWriter writer,
            CatalogChangeJournal journal,
            CatalogLockRepository locks,
            CatalogStudioDocumentService documents,
            CatalogOperationRepository operations,
            Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.documents = Objects.requireNonNull(documents, "documents");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CatalogImportDryRun dryRun(long draftVersionId, long expectedRevision, String format, String document) {
        try (Connection connection = dataSource.getConnection()) {
            CatalogVersionSnapshot draft = versions.loadSnapshot(connection, draftVersionId);
            verifyDraft(draft, draftVersionId, expectedRevision);
            return documents.dryRun(draft, format, document);
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog change-set dry-run failed", exception);
        }
    }

    public CatalogChangeSetApplyResult apply(
            String operationId,
            long draftVersionId,
            long expectedRevision,
            int actorId,
            UUID rootLockToken,
            String format,
            String document,
            String fingerprint,
            String summary) {
        if (operationId == null || operationId.isBlank() || operationId.length() > 96) {
            throw new IllegalArgumentException("Operation ID must contain 1-96 characters");
        }
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
                CatalogRuntimeState runtime = versions.lockRuntimeState(connection);
                if (runtime.draftVersionId() != draftVersionId) {
                    throw new CatalogConcurrentModificationException(draftVersionId, expectedRevision);
                }
                CatalogVersionSnapshot draft = versions.loadSnapshot(connection, draftVersionId);
                verifyDraft(draft, draftVersionId, expectedRevision);
                CatalogLockKey root = new CatalogLockKey(CatalogEntityType.PAGE, CatalogPageType.NORMAL, ROOT_LOCK_ID);
                CatalogLockRecord lock = locks.findActive(connection, draftVersionId, root, clock.instant())
                        .filter(record ->
                                record.ownerId() == actorId && record.token().equals(rootLockToken))
                        .orElseThrow(() -> new CatalogLockConflictException(root));
                CatalogImportDryRun dryRun = documents.dryRun(draft, format, document);
                if (!MessageDigest.isEqual(
                        dryRun.fingerprint().getBytes(StandardCharsets.US_ASCII),
                        Objects.requireNonNull(fingerprint, "fingerprint").getBytes(StandardCharsets.US_ASCII))) {
                    throw new CatalogConcurrentModificationException(draftVersionId, expectedRevision);
                }
                for (CatalogChangeEntry change : dryRun.changes()) writer.apply(connection, draftVersionId, change);
                long revision = versions.incrementRevision(connection, draftVersionId, expectedRevision);
                journal.append(
                        connection, draftVersionId, revision, actorId, summary, dryRun.source(), dryRun.changes());
                operations.insert(
                        connection,
                        new CatalogOperationRecord(
                                operationId,
                                actorId,
                                draftVersionId,
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
                return new CatalogChangeSetApplyResult(
                        revision, dryRun.changes().size(), false);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog change-set apply failed", exception);
        }
    }

    private static void verifyDraft(CatalogVersionSnapshot draft, long draftVersionId, long expectedRevision) {
        if (draft.version().status() != CatalogVersionStatus.DRAFT
                || draft.version().id() != draftVersionId) {
            throw new IllegalArgumentException("Catalog draft not found: " + draftVersionId);
        }
        if (draft.version().revision() != expectedRevision) {
            throw new CatalogConcurrentModificationException(draftVersionId, expectedRevision);
        }
    }
}
