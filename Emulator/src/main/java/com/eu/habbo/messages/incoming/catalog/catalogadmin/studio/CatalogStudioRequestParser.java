package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogStudioDocumentWireCodec;
import com.eu.habbo.messages.ClientMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CatalogStudioRequestParser {
    private CatalogStudioRequestParser() {}

    public static CatalogStudioRevisionRequest parseRevision(ClientMessage packet) {
        Objects.requireNonNull(packet, "packet");
        return new CatalogStudioRevisionRequest(
                packet.readString(),
                positive(packet.readInt(), "draftVersionId"),
                nonNegative(packet.readInt(), "expectedRevision"));
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
        String legacyLockToken = packet.readString();
        UUID lockToken = legacyLockToken.isBlank() ? new UUID(0, 0) : UUID.fromString(legacyLockToken);
        String summary = packet.readString();
        String operationId = packet.bytesAvailable() > 0 ? packet.readString() : "";
        return new CatalogStudioMutationEnvelope(draftVersionId, expectedRevision, lockToken, summary, operationId);
    }

    public static String parseDocument(ClientMessage packet) {
        Objects.requireNonNull(packet, "packet");
        String encodingOrLegacyDocument = packet.readString();
        if (!CatalogStudioDocumentWireCodec.ENCODING.equals(encodingOrLegacyDocument)) {
            return encodingOrLegacyDocument;
        }

        int chunkCount = packet.readInt();
        if (chunkCount < 0 || chunkCount > CatalogStudioDocumentWireCodec.MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid Catalog Studio SQL document chunk count");
        }
        List<String> chunks = new ArrayList<>(chunkCount);
        for (int index = 0; index < chunkCount; index++) chunks.add(packet.readString());
        return CatalogStudioDocumentWireCodec.decode(encodingOrLegacyDocument, chunks);
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
