package com.eu.habbo.habbohotel.catalog.versioning;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/** Applies an operator edit to the live catalog and its private recovery snapshot atomically. */
public final class CatalogLiveMutationService {
    private final DataSource dataSource;
    private final CatalogVersionRepository versions;
    private final CatalogChangeJournal journal;
    private final CatalogSnapshotWriter snapshots;
    private final CatalogLiveEntityWriter live;
    private final CatalogLiveMutationHook hook;
    private final Gson gson;

    public CatalogLiveMutationService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogSnapshotWriter snapshots,
            CatalogLiveEntityWriter live,
            CatalogLiveMutationHook hook,
            Gson gson) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.live = Objects.requireNonNull(live, "live");
        this.hook = Objects.requireNonNull(hook, "hook");
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public CatalogLiveMutationResult apply(CatalogLiveMutationRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(
                request.expectedRevision(),
                request.actorId(),
                request.summary(),
                (connection, activeVersionId) -> buildChange(connection, activeVersionId, request));
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
        return execute(null, actorId, summary, (connection, activeVersionId) -> {
            CatalogPageSnapshot before = versions.loadPage(connection, activeVersionId, catalogType, pageId)
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

    private CatalogLiveMutationResult execute(
            Long expectedRevision, int actorId, String summary, ChangeFactory changeFactory) {
        CatalogChangeEntry committedChange;
        CatalogLiveMutationResult result;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CatalogRuntimeState state = versions.lockRuntimeState(connection);
                CatalogVersion active = versions.loadVersion(connection, state.activeVersionId());
                if (active.status() != CatalogVersionStatus.PUBLISHED) {
                    throw new IllegalStateException("Active catalog recovery snapshot is not published");
                }
                if (expectedRevision != null && active.revision() != expectedRevision) {
                    throw new CatalogConcurrentModificationException(active.id(), expectedRevision);
                }

                committedChange = changeFactory.build(connection, active.id());
                live.apply(connection, committedChange);
                snapshots.apply(connection, active.id(), committedChange);
                long revision = versions.incrementRevision(connection, active.id(), active.revision());
                long groupId = journal.append(
                        connection,
                        active.id(),
                        revision,
                        actorId,
                        summary,
                        CatalogChangeSource.UI,
                        List.of(committedChange));
                result = new CatalogLiveMutationResult(active.id(), revision, journal.load(connection, groupId));
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
        CatalogChangeEntry build(Connection connection, long activeVersionId) throws SQLException;
    }

    private CatalogChangeEntry buildChange(
            Connection connection, long activeVersionId, CatalogLiveMutationRequest request) throws SQLException {
        String beforeJson =
                switch (request.entityType()) {
                    case PAGE ->
                        versions.loadPage(connection, activeVersionId, request.catalogType(), request.entityId())
                                .map(gson::toJson)
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Live catalog page not found: " + request.entityId()));
                    case OFFER ->
                        versions.loadOffer(connection, activeVersionId, request.catalogType(), request.entityId())
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
