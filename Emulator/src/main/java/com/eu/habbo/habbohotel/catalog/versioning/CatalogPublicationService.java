package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

public final class CatalogPublicationService {
    private final DataSource dataSource;
    private final CatalogVersionRepository versions;
    private final CatalogValidationDataRepository validationData;
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
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.validationData = Objects.requireNonNull(validationData, "validationData");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
    }

    public CatalogPublicationResult publish(CatalogPublicationRequest request) {
        Objects.requireNonNull(request, "request");
        CatalogVersionSnapshot publishedSnapshot;
        long nextDraftVersionId;

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
                CatalogValidationReport report =
                        validationData.load(connection).validator().validateChanges(active, draft);
                if (!report.valid()) {
                    connection.rollback();
                    return new CatalogPublicationResult(
                            false, report, runtime.activeVersionId(), runtime.draftVersionId());
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
                new CatalogValidationReport(java.util.List.of()),
                publishedSnapshot.version().id(),
                nextDraftVersionId);
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
