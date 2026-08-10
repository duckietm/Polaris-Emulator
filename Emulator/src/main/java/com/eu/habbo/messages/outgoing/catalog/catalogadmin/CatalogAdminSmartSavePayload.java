package com.eu.habbo.messages.outgoing.catalog.catalogadmin;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeEntry;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeGroup;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogSmartSaveResult;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public record CatalogAdminSmartSavePayload(
        String operationId,
        String action,
        String code,
        long draftVersionId,
        long revision,
        String entityType,
        String catalogType,
        int entityId,
        String entityJson,
        String historyGroupJson,
        String fieldErrorsJson,
        long serverDurationMs) {

    public static final int PROTOCOL_VERSION = 1;

    public CatalogAdminSmartSavePayload {
        operationId = requireText(operationId, "operationId");
        if (operationId.length() > 96) throw new IllegalArgumentException("Operation ID is too long");
        action = requireText(action, "action");
        code = requireText(code, "code");
        if (draftVersionId <= 0) throw new IllegalArgumentException("Draft version ID must be positive");
        if (revision < 0) throw new IllegalArgumentException("Revision cannot be negative");
        entityType = requireText(entityType, "entityType");
        catalogType = requireText(catalogType, "catalogType");
        if (entityId < 0) throw new IllegalArgumentException("Entity ID cannot be negative");
        entityJson = Objects.requireNonNull(entityJson, "entityJson");
        historyGroupJson = Objects.requireNonNull(historyGroupJson, "historyGroupJson");
        fieldErrorsJson = Objects.requireNonNull(fieldErrorsJson, "fieldErrorsJson");
        if (serverDurationMs < 0) throw new IllegalArgumentException("Server duration cannot be negative");
    }

    public int boundedServerDurationMs() {
        return (int) Math.min(serverDurationMs, Integer.MAX_VALUE);
    }

    public byte[] toWireBytes() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeInt(PROTOCOL_VERSION);
            writeString(buffer, operationId);
            writeString(buffer, action);
            writeString(buffer, code);
            buffer.writeInt(Math.toIntExact(draftVersionId));
            buffer.writeInt(Math.toIntExact(revision));
            writeString(buffer, entityType);
            writeString(buffer, catalogType);
            buffer.writeInt(entityId);
            writeString(buffer, entityJson);
            writeString(buffer, historyGroupJson);
            writeString(buffer, fieldErrorsJson);
            buffer.writeInt(boundedServerDurationMs());
            byte[] result = new byte[buffer.readableBytes()];
            buffer.readBytes(result);
            return result;
        } finally {
            buffer.release();
        }
    }

    public static CatalogAdminSmartSavePayload success(
            String action, String code, CatalogSmartSaveResult result, String actorName, Gson gson) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(gson, "gson");
        CatalogChangeGroup group = result.historyGroup();
        HistoryGroupPayload history = new HistoryGroupPayload(
                group.id(),
                group.revision(),
                group.actorId(),
                Objects.requireNonNull(actorName, "actorName"),
                group.summary(),
                group.source().name(),
                group.createdAt().toString(),
                group.entries().stream().map(HistoryEntryPayload::from).toList());
        return new CatalogAdminSmartSavePayload(
                result.operationId(),
                action,
                code,
                result.draftVersionId(),
                result.revision(),
                result.entityType().name(),
                result.catalogType().name(),
                result.entityId(),
                Objects.toString(result.entityJson(), ""),
                gson.toJson(history),
                "{}",
                result.serverDurationMs());
    }

    public static CatalogAdminSmartSavePayload failure(
            String operationId,
            String action,
            String code,
            long draftVersionId,
            long revision,
            String entityType,
            String catalogType,
            int entityId,
            String fieldErrorsJson) {
        return new CatalogAdminSmartSavePayload(
                operationId,
                action,
                code,
                draftVersionId,
                revision,
                entityType,
                catalogType,
                entityId,
                "",
                "null",
                fieldErrorsJson,
                0);
    }

    private static String requireText(String value, String field) {
        value = Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value;
    }

    private static void writeString(ByteBuf buffer, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0xFFFF) throw new IllegalArgumentException("Smart Save string exceeds the wire limit");
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }

    private record HistoryGroupPayload(
            long id,
            long revision,
            int actorId,
            String actorName,
            String summary,
            String source,
            String createdAt,
            List<HistoryEntryPayload> entries) {}

    private record HistoryEntryPayload(String entityType, String catalogType, int entityId, String operation) {
        private static HistoryEntryPayload from(CatalogChangeEntry entry) {
            return new HistoryEntryPayload(
                    entry.entityType().name(),
                    entry.catalogType().name(),
                    entry.entityId(),
                    entry.operation().name());
        }
    }
}
