package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.CatalogPageType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogLockKey;
import com.eu.habbo.messages.ClientMessage;
import java.util.Objects;
import java.util.UUID;

public final class CatalogStudioRequestParser {
    private CatalogStudioRequestParser() {}

    public static CatalogStudioLockRequest parseAcquire(ClientMessage packet) {
        Objects.requireNonNull(packet, "packet");
        return new CatalogStudioLockRequest(
                packet.readString(), positive(packet.readInt(), "draftVersionId"), key(packet), null);
    }

    public static CatalogStudioLockRequest parseTokenLock(ClientMessage packet) {
        Objects.requireNonNull(packet, "packet");
        String operationId = packet.readString();
        long draftVersionId = positive(packet.readInt(), "draftVersionId");
        CatalogLockKey key = key(packet);
        UUID token = UUID.fromString(packet.readString());
        return new CatalogStudioLockRequest(operationId, draftVersionId, key, token);
    }

    public static CatalogStudioRevisionRequest parseRevision(ClientMessage packet) {
        Objects.requireNonNull(packet, "packet");
        return new CatalogStudioRevisionRequest(
                packet.readString(),
                positive(packet.readInt(), "draftVersionId"),
                nonNegative(packet.readInt(), "expectedRevision"));
    }

    public static CatalogStudioRestoreRequest parseRestore(ClientMessage packet) {
        Objects.requireNonNull(packet, "packet");
        return new CatalogStudioRestoreRequest(
                packet.readString(),
                positive(packet.readInt(), "draftVersionId"),
                nonNegative(packet.readInt(), "expectedRevision"),
                positive(packet.readInt(), "sourceVersionId"));
    }

    public static CatalogStudioUndoRequest parseUndo(ClientMessage packet) {
        Objects.requireNonNull(packet, "packet");
        return new CatalogStudioUndoRequest(
                packet.readString(),
                positive(packet.readInt(), "draftVersionId"),
                nonNegative(packet.readInt(), "expectedRevision"),
                positive(packet.readInt(), "groupId"));
    }

    public static CatalogStudioHistoryRequest parseHistory(ClientMessage packet) {
        Objects.requireNonNull(packet, "packet");
        long draftVersionId = positive(packet.readInt(), "draftVersionId");
        int offset = Math.max(0, packet.readInt());
        int limit = Math.max(1, Math.min(packet.readInt(), 100));
        return new CatalogStudioHistoryRequest(draftVersionId, offset, limit);
    }

    public static CatalogStudioMutationEnvelope parseMutationEnvelope(ClientMessage packet) {
        long draftVersionId = positive(packet.readInt(), "draftVersionId");
        long expectedRevision = nonNegative(packet.readInt(), "expectedRevision");
        UUID lockToken = UUID.fromString(packet.readString());
        String summary = packet.readString();
        String operationId = packet.bytesAvailable() > 0 ? packet.readString() : "";
        return new CatalogStudioMutationEnvelope(draftVersionId, expectedRevision, lockToken, summary, operationId);
    }

    private static CatalogLockKey key(ClientMessage packet) {
        CatalogEntityType type = CatalogEntityType.valueOf(packet.readString());
        CatalogPageType catalogType = CatalogPageType.fromString(packet.readString());
        int entityId = Math.toIntExact(positive(packet.readInt(), "entityId"));
        return new CatalogLockKey(type, catalogType, entityId);
    }

    private static long positive(long value, String field) {
        if (value <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    private static long nonNegative(long value, String field) {
        if (value < 0) throw new IllegalArgumentException(field + " cannot be negative");
        return value;
    }
}
