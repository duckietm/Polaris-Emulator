package com.eu.habbo.habbohotel.catalog.versioning;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

public final class CatalogUndoService {
    private final DataSource dataSource;
    private final CatalogVersionRepository versions;
    private final CatalogChangeJournal journal;
    private final CatalogSnapshotWriter writer;
    private final Gson gson;

    public CatalogUndoService(
            DataSource dataSource,
            CatalogVersionRepository versions,
            CatalogChangeJournal journal,
            CatalogSnapshotWriter writer,
            Gson gson) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.versions = Objects.requireNonNull(versions, "versions");
        this.journal = Objects.requireNonNull(journal, "journal");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.gson = Objects.requireNonNull(gson, "gson");
    }

    public long undo(long draftVersionId, long expectedRevision, long groupId, int actorId) {
        return inTransaction(connection -> {
            CatalogVersionSnapshot draft = lockAndLoadDraft(connection, draftVersionId, expectedRevision);
            CatalogChangeGroup group = journal.load(connection, groupId);
            if (group.versionId() != draftVersionId) {
                throw new CatalogUndoConflictException("The selected change belongs to another draft");
            }
            if (journal.hasLaterChangesToSameEntities(connection, group)) {
                throw new CatalogUndoConflictException("One or more entities were edited after the selected change");
            }

            List<CatalogChangeEntry> inverse = inverse(group.entries());
            for (CatalogChangeEntry entry : inverse) writer.apply(connection, draftVersionId, entry);

            long newRevision = versions.incrementRevision(
                    connection, draftVersionId, draft.version().revision());
            journal.append(
                    connection,
                    draftVersionId,
                    newRevision,
                    actorId,
                    "Undo change #" + group.id(),
                    CatalogChangeSource.UNDO,
                    inverse);
            return newRevision;
        });
    }

    public long restore(long draftVersionId, long expectedRevision, long sourceVersionId, int actorId) {
        return inTransaction(connection -> {
            CatalogVersionSnapshot draft = lockAndLoadDraft(connection, draftVersionId, expectedRevision);
            CatalogVersionSnapshot source = versions.loadSnapshot(connection, sourceVersionId);
            if (source == null
                    || (source.version().status() != CatalogVersionStatus.PUBLISHED
                            && source.version().status() != CatalogVersionStatus.ARCHIVED)) {
                throw new CatalogUndoConflictException("Only a previously published catalog version can be restored");
            }

            List<CatalogChangeEntry> changes = diff(draft, source);
            writer.replace(connection, draftVersionId, source);
            long newRevision = versions.incrementRevision(
                    connection, draftVersionId, draft.version().revision());
            journal.append(
                    connection,
                    draftVersionId,
                    newRevision,
                    actorId,
                    "Restore published version #" + sourceVersionId,
                    CatalogChangeSource.RESTORE,
                    changes);
            return newRevision;
        });
    }

    private CatalogVersionSnapshot lockAndLoadDraft(Connection connection, long draftVersionId, long expectedRevision)
            throws SQLException {
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
    }

    private static List<CatalogChangeEntry> inverse(List<CatalogChangeEntry> entries) {
        List<CatalogChangeEntry> inverse = new ArrayList<>(entries.size());
        for (int index = entries.size() - 1; index >= 0; index--) {
            CatalogChangeEntry entry = entries.get(index);
            CatalogChangeOperation operation =
                    switch (entry.operation()) {
                        case CREATE -> CatalogChangeOperation.DELETE;
                        case DELETE -> CatalogChangeOperation.CREATE;
                        case UPDATE -> CatalogChangeOperation.UPDATE;
                        case MOVE -> CatalogChangeOperation.MOVE;
                    };
            inverse.add(new CatalogChangeEntry(
                    0,
                    entry.entityType(),
                    entry.catalogType(),
                    entry.entityId(),
                    operation,
                    entry.afterJson(),
                    entry.beforeJson()));
        }
        return List.copyOf(inverse);
    }

    private List<CatalogChangeEntry> diff(CatalogVersionSnapshot current, CatalogVersionSnapshot target) {
        List<CatalogChangeEntry> entries = new ArrayList<>();
        for (CatalogPageType catalogType : List.of(CatalogPageType.NORMAL, CatalogPageType.BUILDER)) {
            Set<Integer> pageIds = idsForPages(current, catalogType);
            pageIds.addAll(idsForPages(target, catalogType));
            pageIds.stream()
                    .sorted()
                    .forEach(pageId -> addDiff(
                            entries,
                            CatalogEntityType.PAGE,
                            catalogType,
                            pageId,
                            current.page(catalogType, pageId).orElse(null),
                            target.page(catalogType, pageId).orElse(null)));

            Set<Integer> offerIds = idsForOffers(current, catalogType);
            offerIds.addAll(idsForOffers(target, catalogType));
            offerIds.stream()
                    .sorted()
                    .forEach(offerId -> addDiff(
                            entries,
                            CatalogEntityType.OFFER,
                            catalogType,
                            offerId,
                            current.offer(catalogType, offerId).orElse(null),
                            target.offer(catalogType, offerId).orElse(null)));
        }
        entries.sort(Comparator.comparing(CatalogChangeEntry::catalogType)
                .thenComparing(CatalogChangeEntry::entityType)
                .thenComparingInt(CatalogChangeEntry::entityId));
        return List.copyOf(entries);
    }

    private void addDiff(
            List<CatalogChangeEntry> entries,
            CatalogEntityType entityType,
            CatalogPageType catalogType,
            int entityId,
            Object before,
            Object after) {
        String beforeJson = before == null ? null : gson.toJson(before);
        String afterJson = after == null ? null : gson.toJson(after);
        if (Objects.equals(beforeJson, afterJson)) return;
        CatalogChangeOperation operation = before == null
                ? CatalogChangeOperation.CREATE
                : after == null ? CatalogChangeOperation.DELETE : CatalogChangeOperation.UPDATE;
        entries.add(new CatalogChangeEntry(0, entityType, catalogType, entityId, operation, beforeJson, afterJson));
    }

    private static Set<Integer> idsForPages(CatalogVersionSnapshot snapshot, CatalogPageType catalogType) {
        Set<Integer> ids = new HashSet<>();
        snapshot.pages().stream()
                .filter(page -> page.catalogType() == catalogType)
                .forEach(page -> ids.add(page.pageId()));
        return ids;
    }

    private static Set<Integer> idsForOffers(CatalogVersionSnapshot snapshot, CatalogPageType catalogType) {
        Set<Integer> ids = new HashSet<>();
        snapshot.offers().stream()
                .filter(offer -> offer.catalogType() == catalogType)
                .forEach(offer -> ids.add(offer.offerId()));
        return ids;
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
            throw new CatalogVersioningException("Catalog version transaction failed", exception);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
