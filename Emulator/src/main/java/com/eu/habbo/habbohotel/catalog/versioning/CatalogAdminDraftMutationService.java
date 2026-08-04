package com.eu.habbo.habbohotel.catalog.versioning;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;

public final class CatalogAdminDraftMutationService {
    private final DataSource dataSource;
    private final CatalogVersionRepository versions;
    private final CatalogChangeJournal journal;
    private final CatalogSnapshotWriter writer;
    private final CatalogLockRepository locks;
    private final Gson gson;
    private final Clock clock;

    public CatalogAdminDraftMutationService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogSnapshotWriter writer,
            CatalogLockRepository locks,
            Gson gson) {
        this(dataSource, versions, journal, writer, locks, gson, Clock.systemUTC());
    }

    CatalogAdminDraftMutationService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogSnapshotWriter writer,
            CatalogLockRepository locks,
            Gson gson,
            Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CatalogDraftMutationResult apply(CatalogDraftMutationRequest request) {
        CatalogDraftMutationBatchResult batch = applyBatch(List.of(request));
        CatalogChangeEntry change = batch.changes().getFirst();
        return new CatalogDraftMutationResult(batch.revision(), change.entityId(), change);
    }

    public CatalogVersionSnapshot loadDraft(long draftVersionId, long expectedRevision) {
        if (draftVersionId <= 0) throw new IllegalArgumentException("Draft version ID must be positive");
        if (expectedRevision < 0) throw new IllegalArgumentException("Expected revision cannot be negative");
        return inTransaction(connection -> {
            CatalogRuntimeState state = versions.lockRuntimeState(connection);
            if (state.draftVersionId() != draftVersionId) {
                throw new CatalogUndoConflictException("The selected catalog draft is no longer active");
            }
            CatalogVersionSnapshot draft = versions.loadSnapshot(connection, draftVersionId);
            if (draft == null || draft.version().status() != CatalogVersionStatus.DRAFT) {
                throw new CatalogUndoConflictException("Catalog draft not found");
            }
            if (draft.version().revision() != expectedRevision) {
                throw new CatalogConcurrentModificationException(draftVersionId, expectedRevision);
            }
            return draft;
        });
    }

    public CatalogDraftMutationBatchResult applyBatch(List<CatalogDraftMutationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("A catalog mutation batch cannot be empty");
        }
        validateBatch(requests);
        CatalogDraftMutationRequest first = requests.getFirst();
        return inTransaction(connection -> {
            CatalogVersionSnapshot draft = lockAndLoadDraft(connection, first);
            List<CatalogChangeEntry> changes = new ArrayList<>(requests.size());
            Set<String> entityKeys = new HashSet<>();

            for (CatalogDraftMutationRequest request : requests) {
                verifyLock(connection, request);
                CatalogChangeEntry change = buildChange(connection, draft, request);
                String entityKey = change.catalogType() + ":" + change.entityType() + ":" + change.entityId();
                if (!entityKeys.add(entityKey)) {
                    throw new IllegalArgumentException("A mutation batch cannot edit the same entity twice");
                }
                writer.apply(connection, first.draftVersionId(), change);
                changes.add(change);
            }

            long revision = versions.incrementRevision(connection, first.draftVersionId(), first.expectedRevision());
            journal.append(
                    connection,
                    first.draftVersionId(),
                    revision,
                    first.actorId(),
                    first.summary(),
                    CatalogChangeSource.UI,
                    changes);
            return new CatalogDraftMutationBatchResult(revision, changes);
        });
    }

    private CatalogVersionSnapshot lockAndLoadDraft(Connection connection, CatalogDraftMutationRequest request)
            throws SQLException {
        CatalogRuntimeState state = versions.lockRuntimeState(connection);
        if (state.draftVersionId() != request.draftVersionId()) {
            throw new CatalogUndoConflictException("The selected catalog draft is no longer active");
        }
        CatalogVersionSnapshot draft = versions.loadSnapshot(connection, request.draftVersionId());
        if (draft == null || draft.version().status() != CatalogVersionStatus.DRAFT) {
            throw new CatalogUndoConflictException("Catalog draft not found");
        }
        if (draft.version().revision() != request.expectedRevision()) {
            throw new CatalogConcurrentModificationException(request.draftVersionId(), request.expectedRevision());
        }
        return draft;
    }

    private void verifyLock(Connection connection, CatalogDraftMutationRequest request) throws SQLException {
        Optional<CatalogLockRecord> lock =
                locks.findActive(connection, request.draftVersionId(), request.lockKey(), clock.instant());
        if (lock.isEmpty()
                || lock.get().ownerId() != request.actorId()
                || !lock.get().token().equals(request.lockToken())) {
            throw new CatalogLockConflictException(request.lockKey());
        }
    }

    private CatalogChangeEntry buildChange(
            Connection connection, CatalogVersionSnapshot draft, CatalogDraftMutationRequest request)
            throws SQLException {
        if (request.operation() == CatalogChangeOperation.CREATE) {
            return buildCreate(connection, request);
        }

        String beforeJson =
                switch (request.entityType()) {
                    case PAGE ->
                        draft.page(request.catalogType(), request.entityId())
                                .map(gson::toJson)
                                .orElseThrow(() ->
                                        new IllegalArgumentException("Catalog page not found: " + request.entityId()));
                    case OFFER ->
                        draft.offer(request.catalogType(), request.entityId())
                                .map(gson::toJson)
                                .orElseThrow(() ->
                                        new IllegalArgumentException("Catalog offer not found: " + request.entityId()));
                };
        String afterJson = request.afterJson();
        if (afterJson != null) validateExistingPayload(request);
        return new CatalogChangeEntry(
                0,
                request.entityType(),
                request.catalogType(),
                request.entityId(),
                request.operation(),
                beforeJson,
                afterJson);
    }

    private CatalogChangeEntry buildCreate(Connection connection, CatalogDraftMutationRequest request)
            throws SQLException {
        return switch (request.entityType()) {
            case PAGE -> {
                int pageId = Math.toIntExact(versions.nextPageId(connection, request.catalogType()));
                CatalogPageSnapshot page = gson.fromJson(request.afterJson(), CatalogDraftPageData.class)
                        .withId(request.catalogType(), pageId);
                yield new CatalogChangeEntry(
                        0,
                        CatalogEntityType.PAGE,
                        request.catalogType(),
                        pageId,
                        CatalogChangeOperation.CREATE,
                        null,
                        gson.toJson(page));
            }
            case OFFER -> {
                int offerId = Math.toIntExact(versions.nextOfferId(connection, request.catalogType()));
                CatalogOfferSnapshot offer = gson.fromJson(request.afterJson(), CatalogDraftOfferData.class)
                        .withId(request.catalogType(), offerId);
                yield new CatalogChangeEntry(
                        0,
                        CatalogEntityType.OFFER,
                        request.catalogType(),
                        offerId,
                        CatalogChangeOperation.CREATE,
                        null,
                        gson.toJson(offer));
            }
        };
    }

    private void validateExistingPayload(CatalogDraftMutationRequest request) {
        switch (request.entityType()) {
            case PAGE -> {
                CatalogPageSnapshot page = gson.fromJson(request.afterJson(), CatalogPageSnapshot.class);
                if (page.pageId() != request.entityId()) {
                    throw new IllegalArgumentException("Page payload ID does not match the request");
                }
                if (page.catalogType() != request.catalogType()) {
                    throw new IllegalArgumentException("Page payload catalog type does not match the request");
                }
            }
            case OFFER -> {
                CatalogOfferSnapshot offer = gson.fromJson(request.afterJson(), CatalogOfferSnapshot.class);
                if (offer.offerId() != request.entityId()) {
                    throw new IllegalArgumentException("Offer payload ID does not match the request");
                }
                if (offer.catalogType() != request.catalogType()) {
                    throw new IllegalArgumentException("Offer payload catalog type does not match the request");
                }
            }
        }
    }

    private static void validateBatch(List<CatalogDraftMutationRequest> requests) {
        CatalogDraftMutationRequest first = requests.getFirst();
        for (CatalogDraftMutationRequest request : requests) {
            if (request.draftVersionId() != first.draftVersionId()
                    || request.expectedRevision() != first.expectedRevision()
                    || request.actorId() != first.actorId()
                    || !request.summary().equals(first.summary())) {
                throw new IllegalArgumentException(
                        "Every mutation in a batch must share draft, revision, actor, and summary");
            }
        }
    }

    private <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
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
            throw new CatalogVersioningException("Catalog draft mutation failed", exception);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
