package com.eu.habbo.habbohotel.catalog.versioning;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.sql.DataSource;

/** Applies an operator edit to the live catalog and records complete audit/undo history atomically. */
public final class CatalogLiveMutationService {
    private final DataSource dataSource;
    private final CatalogVersionRepository versions;
    private final CatalogChangeJournal journal;
    private final CatalogLiveSnapshotRepository liveSnapshots;
    private final CatalogLiveEntityWriter live;
    private final CatalogLiveMutationHook hook;
    private final CatalogLiveValidationGuard validation;
    private final CatalogOperationRepository operations;
    private final Gson gson;

    public CatalogLiveMutationService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogSnapshotWriter snapshots,
            CatalogLiveEntityWriter live,
            CatalogLiveMutationHook hook,
            Gson gson) {
        this(dataSource, versions, journal, null, live, hook, null, null, gson);
    }

    public CatalogLiveMutationService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogSnapshotWriter snapshots,
            CatalogLiveSnapshotRepository liveSnapshots,
            CatalogLiveEntityWriter live,
            CatalogLiveMutationHook hook,
            CatalogLiveValidationGuard validation,
            Gson gson) {
        this(dataSource, versions, journal, liveSnapshots, live, hook, validation, null, gson);
    }

    public CatalogLiveMutationService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogSnapshotWriter snapshots,
            CatalogLiveSnapshotRepository liveSnapshots,
            CatalogLiveEntityWriter live,
            CatalogLiveMutationHook hook,
            CatalogLiveValidationGuard validation,
            CatalogOperationRepository operations,
            Gson gson) {
        this(dataSource, versions, journal, liveSnapshots, live, hook, validation, operations, gson);
    }

    public CatalogLiveMutationService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogLiveSnapshotRepository liveSnapshots,
            CatalogLiveEntityWriter live,
            CatalogLiveMutationHook hook,
            CatalogLiveValidationGuard validation,
            CatalogOperationRepository operations,
            Gson gson) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.liveSnapshots = liveSnapshots;
        this.live = Objects.requireNonNull(live, "live");
        this.hook = Objects.requireNonNull(hook, "hook");
        this.validation = validation;
        this.operations = operations;
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public CatalogDraftValidationResult validateLive() {
        try (Connection connection = dataSource.getConnection()) {
            CatalogRuntimeState state = versions.lockRuntimeState(connection);
            CatalogVersionSnapshot active = loadPhysicalLive(connection, state);
            if (validation == null) {
                return new CatalogDraftValidationResult(
                        active.version().revision(), new CatalogValidationReport(List.of()));
            }
            return new CatalogDraftValidationResult(active.version().revision(), validation.report(connection, active));
        } catch (SQLException exception) {
            throw new CatalogVersioningException("Live catalog validation failed", exception);
        }
    }

    public CatalogVersionSnapshot loadLive() {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CatalogRuntimeState state = versions.lockRuntimeState(connection);
                CatalogVersionSnapshot active = loadPhysicalLive(connection, state);
                if (active.version().status() != CatalogVersionStatus.PUBLISHED) {
                    throw new IllegalStateException("Live catalog state is not available");
                }
                connection.commit();
                return active;
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
            throw new CatalogVersioningException("Live catalog load failed", exception);
        }
    }

    public CatalogLiveMutationResult apply(CatalogLiveMutationRequest request) {
        return apply(request, CatalogLiveMutationPrecondition.NONE);
    }

    public CatalogLiveMutationResult apply(
            CatalogLiveMutationRequest request, CatalogLiveMutationPrecondition precondition) {
        Objects.requireNonNull(request, "request");
        CatalogLiveMutationBatchResult batch = applyBatch(List.of(request), precondition);
        return new CatalogLiveMutationResult(batch.activeVersionId(), batch.revision(), batch.historyGroup());
    }

    public CatalogLiveMutationBatchResult applyBatch(List<CatalogLiveMutationRequest> requests) {
        return applyBatch(requests, CatalogLiveMutationPrecondition.NONE);
    }

    public CatalogLiveMutationBatchResult applyBatch(
            List<CatalogLiveMutationRequest> requests, CatalogLiveMutationPrecondition precondition) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("A live mutation batch cannot be empty");
        }
        Objects.requireNonNull(precondition, "precondition");
        CatalogLiveMutationRequest first = requests.getFirst();
        for (CatalogLiveMutationRequest request : requests) {
            if (request.expectedRevision() != first.expectedRevision()
                    || request.actorId() != first.actorId()
                    || !request.summary().equals(first.summary())
                    || !request.operationId().equals(first.operationId())) {
                throw new IllegalArgumentException(
                        "Every live mutation must share revision, actor, summary, and operation ID");
            }
        }
        String requestFingerprint = fingerprint(requests);

        List<CatalogChangeEntry> committedChanges = new ArrayList<>();
        CatalogLiveMutationBatchResult result;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CatalogRuntimeState state = versions.lockRuntimeState(connection);
                CatalogVersionSnapshot active = loadPhysicalLive(connection, state);
                if (active.version().status() != CatalogVersionStatus.PUBLISHED) {
                    throw new IllegalStateException("Live catalog state is not available");
                }
                if (operations != null && !first.operationId().isEmpty()) {
                    var replay = operations.findForUpdate(connection, first.operationId(), first.actorId());
                    if (replay.isPresent()) {
                        CatalogOperationRecord record = replay.get();
                        if (!requestFingerprint.equals(record.requestFingerprint())
                                || record.historyGroupId() == null) {
                            throw new IllegalArgumentException(
                                    "Operation ID was already used for different catalog content");
                        }
                        CatalogChangeGroup group = journal.load(connection, record.historyGroupId());
                        connection.commit();
                        return new CatalogLiveMutationBatchResult(
                                record.versionId(), record.resultRevision(), group, group.entries());
                    }
                }
                if (first.expectedRevision() >= 0 && active.version().revision() != first.expectedRevision()) {
                    throw new CatalogConcurrentModificationException(
                            active.version().id(), first.expectedRevision());
                }
                precondition.validate(active);
                Set<String> entityKeys = new HashSet<>();
                for (CatalogLiveMutationRequest request : requests) {
                    CatalogChangeEntry change = buildChange(connection, active, request);
                    String key = change.catalogType() + ":" + change.entityType() + ":" + change.entityId();
                    if (!entityKeys.add(key)) {
                        throw new IllegalArgumentException("A live mutation batch cannot edit the same entity twice");
                    }
                    committedChanges.add(change);
                }
                if (validation != null) validation.rejectIntroducedProblems(connection, active, committedChanges);
                for (CatalogChangeEntry change : committedChanges) {
                    live.apply(connection, change);
                }
                long revision = versions.incrementRevision(
                        connection, active.version().id(), active.version().revision());
                long groupId = journal.append(
                        connection,
                        active.version().id(),
                        revision,
                        first.actorId(),
                        first.summary(),
                        CatalogChangeSource.UI,
                        committedChanges);
                if (operations != null && !first.operationId().isEmpty()) {
                    CatalogChangeEntry primary = committedChanges.getFirst();
                    operations.insert(
                            connection,
                            new CatalogOperationRecord(
                                    first.operationId(),
                                    first.actorId(),
                                    active.version().id(),
                                    CatalogChangeSource.UI,
                                    revision,
                                    requestFingerprint,
                                    primary.operation(),
                                    primary.entityType(),
                                    primary.catalogType(),
                                    primary.entityId(),
                                    groupId,
                                    null));
                }
                result = new CatalogLiveMutationBatchResult(
                        active.version().id(), revision, journal.load(connection, groupId), committedChanges);
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
            throw new CatalogVersioningException("Live catalog mutation failed", exception);
        }
        committedChanges.forEach(hook::afterCommit);
        return result;
    }

    public CatalogLiveMutationResult setPageVisible(
            int actorId,
            String summary,
            com.eu.habbo.habbohotel.catalog.CatalogPageType catalogType,
            int pageId,
            boolean visible) {
        if (actorId <= 0) throw new IllegalArgumentException("Actor ID must be positive");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(catalogType, "catalogType");
        if (pageId <= 0) throw new IllegalArgumentException("Page ID must be positive");
        return execute(null, actorId, summary, (connection, active) -> {
            CatalogPageSnapshot before = active.page(catalogType, pageId)
                    .orElseThrow(() -> new IllegalArgumentException("Live catalog page not found: " + pageId));
            CatalogPageSnapshot after = CatalogSnapshotPatch.setPageVisible(before, visible);
            return new CatalogChangeEntry(
                    0,
                    CatalogEntityType.PAGE,
                    catalogType,
                    pageId,
                    CatalogChangeOperation.UPDATE,
                    gson.toJson(before),
                    gson.toJson(after));
        });
    }

    public CatalogLiveMutationResult setPageVisible(
            long expectedRevision,
            String operationId,
            int actorId,
            String summary,
            com.eu.habbo.habbohotel.catalog.CatalogPageType catalogType,
            int pageId,
            boolean visible) {
        return updatePage(
                expectedRevision,
                operationId,
                actorId,
                summary,
                catalogType,
                pageId,
                page -> CatalogSnapshotPatch.setPageVisible(page, visible),
                CatalogChangeOperation.UPDATE);
    }

    public CatalogLiveMutationResult updatePage(
            int actorId,
            String summary,
            com.eu.habbo.habbohotel.catalog.CatalogPageType catalogType,
            int pageId,
            UnaryOperator<CatalogPageSnapshot> patch,
            CatalogChangeOperation operation) {
        Objects.requireNonNull(patch, "patch");
        return execute(null, actorId, summary, (connection, active) -> {
            CatalogPageSnapshot before = active.page(catalogType, pageId)
                    .orElseThrow(() -> new IllegalArgumentException("Live catalog page not found: " + pageId));
            CatalogPageSnapshot after = Objects.requireNonNull(patch.apply(before), "patched page");
            return new CatalogChangeEntry(
                    0, CatalogEntityType.PAGE, catalogType, pageId, operation, gson.toJson(before), gson.toJson(after));
        });
    }

    public CatalogLiveMutationResult updatePage(
            long expectedRevision,
            String operationId,
            int actorId,
            String summary,
            com.eu.habbo.habbohotel.catalog.CatalogPageType catalogType,
            int pageId,
            UnaryOperator<CatalogPageSnapshot> patch,
            CatalogChangeOperation operation) {
        CatalogPageSnapshot before = loadLive()
                .page(catalogType, pageId)
                .orElseThrow(() -> new IllegalArgumentException("Live catalog page not found: " + pageId));
        CatalogPageSnapshot after = Objects.requireNonNull(patch.apply(before), "patched page");
        return apply(new CatalogLiveMutationRequest(
                expectedRevision,
                actorId,
                summary,
                CatalogEntityType.PAGE,
                catalogType,
                pageId,
                operation,
                gson.toJson(after),
                operationId));
    }

    public CatalogLiveMutationResult updateOffer(
            int actorId,
            String summary,
            com.eu.habbo.habbohotel.catalog.CatalogPageType catalogType,
            int offerId,
            UnaryOperator<CatalogOfferSnapshot> patch,
            CatalogChangeOperation operation) {
        Objects.requireNonNull(patch, "patch");
        return execute(null, actorId, summary, (connection, active) -> {
            CatalogOfferSnapshot before = active.offer(catalogType, offerId)
                    .orElseThrow(() -> new IllegalArgumentException("Live catalog offer not found: " + offerId));
            CatalogOfferSnapshot after = Objects.requireNonNull(patch.apply(before), "patched offer");
            return new CatalogChangeEntry(
                    0,
                    CatalogEntityType.OFFER,
                    catalogType,
                    offerId,
                    operation,
                    gson.toJson(before),
                    gson.toJson(after));
        });
    }

    public CatalogLiveMutationResult updateOffer(
            long expectedRevision,
            String operationId,
            int actorId,
            String summary,
            com.eu.habbo.habbohotel.catalog.CatalogPageType catalogType,
            int offerId,
            UnaryOperator<CatalogOfferSnapshot> patch,
            CatalogChangeOperation operation) {
        CatalogOfferSnapshot before = loadLive()
                .offer(catalogType, offerId)
                .orElseThrow(() -> new IllegalArgumentException("Live catalog offer not found: " + offerId));
        CatalogOfferSnapshot after = Objects.requireNonNull(patch.apply(before), "patched offer");
        return apply(new CatalogLiveMutationRequest(
                expectedRevision,
                actorId,
                summary,
                CatalogEntityType.OFFER,
                catalogType,
                offerId,
                operation,
                gson.toJson(after),
                operationId));
    }

    private CatalogLiveMutationResult execute(
            Long expectedRevision, int actorId, String summary, ChangeFactory changeFactory) {
        CatalogChangeEntry committedChange;
        CatalogLiveMutationResult result;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CatalogRuntimeState state = versions.lockRuntimeState(connection);
                CatalogVersionSnapshot active = loadPhysicalLive(connection, state);
                if (active.version().status() != CatalogVersionStatus.PUBLISHED) {
                    throw new IllegalStateException("Live catalog state is not available");
                }
                if (expectedRevision != null && active.version().revision() != expectedRevision) {
                    throw new CatalogConcurrentModificationException(
                            active.version().id(), expectedRevision);
                }

                committedChange = changeFactory.build(connection, active);
                if (validation != null)
                    validation.rejectIntroducedProblems(connection, active, List.of(committedChange));
                live.apply(connection, committedChange);
                long revision = versions.incrementRevision(
                        connection, active.version().id(), active.version().revision());
                long groupId = journal.append(
                        connection,
                        active.version().id(),
                        revision,
                        actorId,
                        summary,
                        CatalogChangeSource.UI,
                        List.of(committedChange));
                result = new CatalogLiveMutationResult(
                        active.version().id(), revision, journal.load(connection, groupId));
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
            throw new CatalogVersioningException("Live catalog mutation failed", exception);
        }
        hook.afterCommit(committedChange);
        return result;
    }

    @FunctionalInterface
    private interface ChangeFactory {
        CatalogChangeEntry build(Connection connection, CatalogVersionSnapshot active) throws SQLException;
    }

    private CatalogVersionSnapshot loadPhysicalLive(Connection connection, CatalogRuntimeState state)
            throws SQLException {
        CatalogVersion version = versions.loadVersion(connection, state.activeVersionId());
        if (version.status() != CatalogVersionStatus.PUBLISHED) {
            throw new IllegalStateException("Live catalog state is not available");
        }
        return liveSnapshots == null
                ? versions.loadSnapshot(connection, state.activeVersionId())
                : liveSnapshots.load(connection, version);
    }

    private String fingerprint(List<CatalogLiveMutationRequest> requests) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CatalogLiveMutationRequest request : requests) {
                digest.update(request.summary().getBytes(StandardCharsets.UTF_8));
                digest.update(request.entityType().name().getBytes(StandardCharsets.US_ASCII));
                digest.update(request.catalogType().name().getBytes(StandardCharsets.US_ASCII));
                digest.update(Integer.toString(request.entityId()).getBytes(StandardCharsets.US_ASCII));
                digest.update(request.operation().name().getBytes(StandardCharsets.US_ASCII));
                if (request.afterJson() != null)
                    digest.update(request.afterJson().getBytes(StandardCharsets.UTF_8));
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private CatalogChangeEntry buildChange(
            Connection connection, CatalogVersionSnapshot active, CatalogLiveMutationRequest request)
            throws SQLException {
        if (request.operation() == CatalogChangeOperation.CREATE) {
            return buildCreate(connection, request);
        }
        String beforeJson =
                switch (request.entityType()) {
                    case PAGE ->
                        active.page(request.catalogType(), request.entityId())
                                .map(gson::toJson)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Live catalog page not found: " + request.entityId()));
                    case OFFER ->
                        active.offer(request.catalogType(), request.entityId())
                                .map(gson::toJson)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Live catalog offer not found: " + request.entityId()));
                };
        validateIdentity(request);
        return new CatalogChangeEntry(
                0,
                request.entityType(),
                request.catalogType(),
                request.entityId(),
                request.operation(),
                beforeJson,
                request.operation() == CatalogChangeOperation.DELETE ? null : request.afterJson());
    }

    private CatalogChangeEntry buildCreate(Connection connection, CatalogLiveMutationRequest request)
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

    private void validateIdentity(CatalogLiveMutationRequest request) {
        if (request.operation() == CatalogChangeOperation.DELETE) return;
        boolean valid =
                switch (request.entityType()) {
                    case PAGE -> {
                        CatalogPageSnapshot page = gson.fromJson(request.afterJson(), CatalogPageSnapshot.class);
                        yield page.pageId() == request.entityId() && page.catalogType() == request.catalogType();
                    }
                    case OFFER -> {
                        CatalogOfferSnapshot offer = gson.fromJson(request.afterJson(), CatalogOfferSnapshot.class);
                        yield offer.offerId() == request.entityId() && offer.catalogType() == request.catalogType();
                    }
                };
        if (!valid) throw new IllegalArgumentException("Live catalog payload identity does not match the request");
    }
}
