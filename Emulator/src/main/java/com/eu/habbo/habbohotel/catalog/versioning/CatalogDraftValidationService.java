package com.eu.habbo.habbohotel.catalog.versioning;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

public final class CatalogDraftValidationService {
    private final DataSource dataSource;
    private final CatalogVersionRepository versions;
    private final CatalogValidationDataRepository validationData;

    public CatalogDraftValidationService(
            DataSource dataSource, CatalogVersionRepository versions, CatalogValidationDataRepository validationData) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.validationData = Objects.requireNonNull(validationData, "validationData");
    }

    public CatalogDraftValidationResult validate(long draftVersionId, long expectedRevision) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CatalogRuntimeState runtime = versions.lockRuntimeState(connection);
                CatalogVersionSnapshot draft = loadDraft(connection, runtime, draftVersionId, expectedRevision);
                CatalogVersionSnapshot active = versions.loadSnapshot(connection, runtime.activeVersionId());
                CatalogValidationReport report =
                        validationData.load(connection).validator().validateChanges(active, draft);
                connection.commit();
                return new CatalogDraftValidationResult(draft.version().revision(), report);
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Catalog draft validation failed", exception);
        }
    }

    private CatalogVersionSnapshot loadDraft(
            Connection connection, CatalogRuntimeState runtime, long draftVersionId, long expectedRevision)
            throws SQLException {
        if (runtime.draftVersionId() != draftVersionId) {
            throw new CatalogConcurrentModificationException(draftVersionId, expectedRevision);
        }
        CatalogVersionSnapshot draft = versions.loadSnapshot(connection, draftVersionId);
        if (draft.version().status() != CatalogVersionStatus.DRAFT
                || draft.version().revision() != expectedRevision) {
            throw new CatalogConcurrentModificationException(draftVersionId, expectedRevision);
        }
        return draft;
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
