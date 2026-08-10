package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CatalogAdminSmartSaveService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogAdminSmartSaveService.class);

    private final DataSource dataSource;
    private final CatalogVersionRepository versions;
    private final CatalogChangeJournal journal;
    private final CatalogSnapshotWriter writer;
    private final CatalogLockRepository locks;
    private final CatalogOperationRepository operations;
    private final Gson gson;
    private final Clock clock;

    public CatalogAdminSmartSaveService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogSnapshotWriter writer,
            CatalogLockRepository locks,
            CatalogOperationRepository operations,
            Gson gson) {
        this(dataSource, versions, journal, writer, locks, operations, gson, Clock.systemUTC());
    }

    CatalogAdminSmartSaveService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogSnapshotWriter writer,
            CatalogLockRepository locks,
            CatalogOperationRepository operations,
            Gson gson,
            Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.locks = Objects.requireNonNull(locks, "locks");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CatalogSmartSaveResult apply(CatalogSmartSaveRequest request, CatalogSmartSavePrecondition precondition) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(precondition, "precondition");
        long startedAt = System.nanoTime();
        String fingerprint = fingerprint(request);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CatalogSmartSaveResult result =
                        applyInTransaction(connection, request, precondition, fingerprint, startedAt);
                connection.commit();
                LOGGER.debug(
                        "Catalog Smart Save operation={} action={} entity={}:{}:{} replayed={} revision={} durationMs={}",
                        result.operationId(),
                        result.operation(),
                        result.catalogType(),
                        result.entityType(),
                        result.entityId(),
                        result.replayed(),
                        result.revision(),
                        result.serverDurationMs());
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
            throw new CatalogVersioningException("Catalog Smart Save failed", exception);
        }
    }

    private CatalogSmartSaveResult applyInTransaction(
            Connection connection,
            CatalogSmartSaveRequest request,
            CatalogSmartSavePrecondition precondition,
            String fingerprint,
            long startedAt)
            throws SQLException {
        CatalogRuntimeState state = versions.lockRuntimeState(connection);
        if (state.draftVersionId() != request.draftVersionId()) {
            throw new CatalogUndoConflictException("The selected catalog draft is no longer active");
        }

        Optional<CatalogOperationRecord> replay =
                operations.findForUpdate(connection, request.operationId(), request.actorId());
        if (replay.isPresent()) return replay(connection, request, fingerprint, replay.get());

        CatalogVersion version = versions.loadVersion(connection, request.draftVersionId());
        if (version.status() != CatalogVersionStatus.DRAFT) {
            throw new CatalogUndoConflictException("Catalog draft not found");
        }
        if (version.revision() != request.expectedRevision()) {
            throw new CatalogConcurrentModificationException(request.draftVersionId(), request.expectedRevision());
        }

        verifyLock(connection, request);
        CatalogSmartSaveDraft draft = new CatalogSmartSaveDraft(connection, versions, request.draftVersionId());
        precondition.validate(draft);
        CatalogChangeEntry change = buildChange(connection, request, draft);
        writer.apply(connection, request.draftVersionId(), change);
        long revision = versions.incrementRevision(connection, request.draftVersionId(), request.expectedRevision());
        long groupId = journal.append(
                connection,
                request.draftVersionId(),
                revision,
                request.actorId(),
                request.summary(),
                CatalogChangeSource.UI,
                List.of(change));
        CatalogChangeGroup historyGroup = journal.load(connection, groupId);
        long durationMs = elapsedMillis(startedAt);
        CatalogSmartSaveResult result = new CatalogSmartSaveResult(
                false,
                request.operationId(),
                request.draftVersionId(),
                revision,
                request.entityType(),
                request.lockKey().catalogType(),
                change.entityId(),
                request.operation(),
                historyGroup,
                change.afterJson(),
                durationMs);
        PersistedResult persisted = PersistedResult.from(result);
        operations.insert(
                connection,
                new CatalogOperationRecord(
                        request.operationId(),
                        request.actorId(),
                        request.draftVersionId(),
                        CatalogChangeSource.UI,
                        revision,
                        fingerprint,
                        request.operation(),
                        request.entityType(),
                        request.lockKey().catalogType(),
                        change.entityId(),
                        groupId,
                        gson.toJson(persisted)));
        return result;
    }

    private CatalogSmartSaveResult replay(
            Connection connection, CatalogSmartSaveRequest request, String fingerprint, CatalogOperationRecord record)
            throws SQLException {
        if (!Objects.equals(record.requestFingerprint(), fingerprint)) {
            throw new IllegalArgumentException("Operation ID was already used for different catalog content");
        }
        if (record.resultJson() == null || record.historyGroupId() == null) {
            throw new IllegalArgumentException("Operation ID belongs to an incompatible catalog operation");
        }
        PersistedResult persisted = gson.fromJson(record.resultJson(), PersistedResult.class);
        CatalogChangeGroup historyGroup = journal.load(connection, record.historyGroupId());
        return persisted.toResult(historyGroup).asReplay();
    }

    private void verifyLock(Connection connection, CatalogSmartSaveRequest request) throws SQLException {
        Optional<CatalogLockRecord> lock =
                locks.findActive(connection, request.draftVersionId(), request.lockKey(), clock.instant());
        if (lock.isEmpty()
                || lock.get().ownerId() != request.actorId()
                || !lock.get().token().equals(request.lockToken())) {
            throw new CatalogLockConflictException(request.lockKey());
        }
    }

    private CatalogChangeEntry buildChange(
            Connection connection, CatalogSmartSaveRequest request, CatalogSmartSaveDraft draft) throws SQLException {
        if (request.operation() == CatalogChangeOperation.CREATE) {
            return buildCreate(connection, request);
        }
        String beforeJson =
                switch (request.entityType()) {
                    case PAGE ->
                        draft.page(request.lockKey().catalogType(), request.entityId())
                                .map(gson::toJson)
                                .orElseThrow(() ->
                                        new IllegalArgumentException("Catalog page not found: " + request.entityId()));
                    case OFFER ->
                        draft.offer(request.lockKey().catalogType(), request.entityId())
                                .map(gson::toJson)
                                .orElseThrow(() ->
                                        new IllegalArgumentException("Catalog offer not found: " + request.entityId()));
                };
        String afterJson = normalizeExistingPayload(request, beforeJson);
        return new CatalogChangeEntry(
                0,
                request.entityType(),
                request.lockKey().catalogType(),
                request.entityId(),
                request.operation(),
                beforeJson,
                afterJson);
    }

    private CatalogChangeEntry buildCreate(Connection connection, CatalogSmartSaveRequest request) throws SQLException {
        CatalogPageType catalogType = request.lockKey().catalogType();
        return switch (request.entityType()) {
            case PAGE -> {
                int pageId = Math.toIntExact(versions.nextPageId(connection, catalogType));
                CatalogPageSnapshot page = gson.fromJson(request.afterJson(), CatalogDraftPageData.class)
                        .withId(catalogType, pageId);
                yield new CatalogChangeEntry(
                        0,
                        CatalogEntityType.PAGE,
                        catalogType,
                        pageId,
                        CatalogChangeOperation.CREATE,
                        null,
                        gson.toJson(page));
            }
            case OFFER -> {
                int offerId = Math.toIntExact(versions.nextOfferId(connection, catalogType));
                CatalogOfferSnapshot offer = gson.fromJson(request.afterJson(), CatalogDraftOfferData.class)
                        .withId(catalogType, offerId);
                yield new CatalogChangeEntry(
                        0,
                        CatalogEntityType.OFFER,
                        catalogType,
                        offerId,
                        CatalogChangeOperation.CREATE,
                        null,
                        gson.toJson(offer));
            }
        };
    }

    private String normalizeExistingPayload(CatalogSmartSaveRequest request, String beforeJson) {
        String afterJson = request.afterJson();
        if (request.entityType() == CatalogEntityType.OFFER
                && !JsonParser.parseString(afterJson).getAsJsonObject().has("offerId")) {
            CatalogDraftOfferData draft = gson.fromJson(afterJson, CatalogDraftOfferData.class);
            CatalogOfferSnapshot before = gson.fromJson(beforeJson, CatalogOfferSnapshot.class);
            String itemIds = draft.itemIds().isBlank() ? before.itemIds() : draft.itemIds();
            afterJson = gson.toJson(new CatalogOfferSnapshot(
                    request.lockKey().catalogType(),
                    request.entityId(),
                    itemIds,
                    draft.pageId(),
                    draft.catalogName(),
                    draft.costCredits(),
                    draft.costPoints(),
                    draft.pointsType(),
                    draft.amount(),
                    draft.limitedStack(),
                    draft.orderNumber(),
                    draft.offerIdClient(),
                    draft.songId(),
                    draft.extradata(),
                    draft.haveOffer(),
                    draft.clubOnly()));
        }

        switch (request.entityType()) {
            case PAGE -> {
                CatalogPageSnapshot page = gson.fromJson(afterJson, CatalogPageSnapshot.class);
                if (page.pageId() != request.entityId()
                        || page.catalogType() != request.lockKey().catalogType()) {
                    throw new IllegalArgumentException("Page payload identity does not match the request");
                }
            }
            case OFFER -> {
                CatalogOfferSnapshot offer = gson.fromJson(afterJson, CatalogOfferSnapshot.class);
                if (offer.offerId() != request.entityId()
                        || offer.catalogType() != request.lockKey().catalogType()) {
                    throw new IllegalArgumentException("Offer payload identity does not match the request");
                }
            }
        }
        return afterJson;
    }

    static String fingerprint(CatalogSmartSaveRequest request) {
        String canonical = request.draftVersionId()
                + "\u0000" + request.actorId()
                + "\u0000" + request.operation()
                + "\u0000" + request.lockKey().catalogType()
                + "\u0000" + request.entityType()
                + "\u0000" + request.entityId()
                + "\u0000" + Objects.toString(request.afterJson(), "");
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private record PersistedResult(
            String operationId,
            long draftVersionId,
            long revision,
            CatalogEntityType entityType,
            CatalogPageType catalogType,
            int entityId,
            CatalogChangeOperation operation,
            String entityJson,
            long serverDurationMs) {

        private static PersistedResult from(CatalogSmartSaveResult result) {
            return new PersistedResult(
                    result.operationId(),
                    result.draftVersionId(),
                    result.revision(),
                    result.entityType(),
                    result.catalogType(),
                    result.entityId(),
                    result.operation(),
                    result.entityJson(),
                    result.serverDurationMs());
        }

        private CatalogSmartSaveResult toResult(CatalogChangeGroup historyGroup) {
            return new CatalogSmartSaveResult(
                    false,
                    operationId,
                    draftVersionId,
                    revision,
                    entityType,
                    catalogType,
                    entityId,
                    operation,
                    historyGroup,
                    entityJson,
                    serverDurationMs);
        }
    }
}
