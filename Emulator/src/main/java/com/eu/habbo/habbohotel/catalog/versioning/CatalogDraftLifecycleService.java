package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

public final class CatalogDraftLifecycleService {
    private final DataSource dataSource;
    private final CatalogVersionRepository versions;
    private final CatalogLockRepository locks;

    public CatalogDraftLifecycleService(
            DataSource dataSource, CatalogVersionRepository versions, CatalogLockRepository locks) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.locks = Objects.requireNonNull(locks, "locks");
    }

    public CatalogDraftDiscardResult discard(
            long draftVersionId, long expectedRevision, int actorId, String nextDraftLabel) {
        if (actorId <= 0) throw new IllegalArgumentException("Actor ID must be positive");
        Objects.requireNonNull(nextDraftLabel, "nextDraftLabel");

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CatalogRuntimeState state = versions.lockRuntimeState(connection);
                if (state.draftVersionId() != draftVersionId) {
                    throw new CatalogConcurrentModificationException(draftVersionId, expectedRevision);
                }
                CatalogVersionSnapshot draft = versions.loadSnapshot(connection, draftVersionId);
                if (draft.version().status() != CatalogVersionStatus.DRAFT
                        || draft.version().revision() != expectedRevision) {
                    throw new CatalogConcurrentModificationException(draftVersionId, expectedRevision);
                }

                versions.archiveVersion(connection, draftVersionId);
                long nextDraftVersionId =
                        versions.cloneAsDraft(connection, state.activeVersionId(), actorId, nextDraftLabel);
                versions.updateRuntimePointers(connection, state.activeVersionId(), nextDraftVersionId);
                locks.releaseAll(connection, draftVersionId);
                connection.commit();
                return new CatalogDraftDiscardResult(state.activeVersionId(), nextDraftVersionId, 0);
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog draft discard failed", exception);
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
