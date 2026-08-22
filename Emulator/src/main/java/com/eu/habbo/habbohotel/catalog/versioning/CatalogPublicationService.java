package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

public final class CatalogPublicationService {
    private final DataSource dataSource;
    private final CatalogVersionRepository versions;
    private final CatalogValidationDataRepository validationData;
    private final CatalogLiveReconciler reconciler;
    private final CatalogLiveProjection projection;
    private final CatalogLockRepository locks;
    private final CatalogPublicationHooks hooks;

    public CatalogPublicationService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogValidationDataRepository validationData,
            CatalogLiveProjection projection,
            CatalogLockRepository locks,
            CatalogPublicationHooks hooks) {
        this(
                dataSource,
                versions,
                validationData,
                (connection, active, draft, actorId) ->
                        new CatalogLiveReconciliationResult(draft, 0, java.util.List.of()),
                projection,
                locks,
                hooks);
    }

    public CatalogPublicationService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogValidationDataRepository validationData,
            CatalogLiveReconciler reconciler,
            CatalogLiveProjection projection,
            CatalogLockRepository locks,
            CatalogPublicationHooks hooks) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.validationData = Objects.requireNonNull(validationData, "validationData");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
    }

    public CatalogPublicationResult publish(CatalogPublicationRequest request) {
        Objects.requireNonNull(request, "request");
        CatalogVersionSnapshot publishedSnapshot;
        long nextDraftVersionId;
        int importedChanges;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CatalogRuntimeState runtime = versions.lockRuntimeState(connection);
                if (runtime.draftVersionId() != request.draftVersionId()) {
                    throw new CatalogConcurrentModificationException(
                            request.draftVersionId(), request.expectedRevision());
                }

                CatalogVersionSnapshot draft = versions.loadSnapshot(connection, request.draftVersionId());
                if (draft.version().status() != CatalogVersionStatus.DRAFT
                        || draft.version().revision() != request.expectedRevision()) {
                    throw new CatalogConcurrentModificationException(
                            request.draftVersionId(), request.expectedRevision());
                }

                CatalogVersionSnapshot active = versions.loadSnapshot(connection, runtime.activeVersionId());
                CatalogLiveReconciliationResult reconciliation =
                        reconciler.reconcile(connection, active, draft, request.actorId());
                if (!reconciliation.conflicts().isEmpty()) {
                    connection.rollback();
                    return new CatalogPublicationResult(
                            false,
                            false,
                            new CatalogValidationReport(java.util.List.of()),
                            runtime.activeVersionId(),
                            runtime.draftVersionId(),
                            draft.version().revision(),
                            0,
                            reconciliation.conflicts());
                }
                draft = reconciliation.snapshot();
                importedChanges = reconciliation.importedChanges();
                CatalogValidationReport report =
                        validationData.load(connection).validator().validateChanges(active, draft);
                if (!report.valid()) {
                    connection.rollback();
                    return new CatalogPublicationResult(
                            false,
                            false,
                            report,
                            runtime.activeVersionId(),
                            runtime.draftVersionId(),
                            request.expectedRevision(),
                            0,
                            java.util.List.of());
                }

                if (sameCatalog(active, draft)) {
                    connection.commit();
                    return new CatalogPublicationResult(
                            false,
                            true,
                            new CatalogValidationReport(java.util.List.of()),
                            runtime.activeVersionId(),
                            runtime.draftVersionId(),
                            draft.version().revision(),
                            0,
                            java.util.List.of());
                }

                projection.replace(connection, draft);
                versions.archiveVersion(connection, runtime.activeVersionId());
                versions.markPublished(connection, request.draftVersionId(), request.actorId());
                nextDraftVersionId = versions.cloneAsDraft(
                        connection, request.draftVersionId(), request.actorId(), request.nextDraftLabel());
                versions.updateRuntimePointers(connection, request.draftVersionId(), nextDraftVersionId);
                locks.releaseAll(connection, request.draftVersionId());
                connection.commit();
                publishedSnapshot = draft;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new CatalogPublicationException("Catalog publication failed", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof CatalogPublicationException publicationException) {
                throw publicationException;
            }
            throw new CatalogPublicationException("Catalog publication failed", exception);
        }

        hooks.afterCommit(publishedSnapshot, nextDraftVersionId);
        return new CatalogPublicationResult(
                true,
                false,
                new CatalogValidationReport(java.util.List.of()),
                publishedSnapshot.version().id(),
                nextDraftVersionId,
                publishedSnapshot.version().revision(),
                importedChanges,
                java.util.List.of());
    }

    private static boolean sameCatalog(CatalogVersionSnapshot left, CatalogVersionSnapshot right) {
        return left.pages().equals(right.pages()) && left.offers().equals(right.offers());
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
