package com.eu.habbo.messages.outgoing.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogDraftPreview;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPageSnapshot;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioActor;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioChangedEntity;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioDocumentResultComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioHistoryComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioHistoryEntry;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioHistoryGroup;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioLockComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioOperationComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioPreviewComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioPublishedVersion;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioSessionComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioValidationComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioValidationIssue;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

class CatalogStudioPacketContractTest {

    @Test
    void sessionPayloadKeepsVersionActorsValidationAndPublishedVersionsInOrder() {
        ByteBuf payload = new CatalogStudioSessionComposer(
                        11,
                        12,
                        7,
                        Instant.parse("2026-08-02T10:00:00Z"),
                        Instant.parse("2026-08-02T10:05:00Z"),
                        3,
                        List.of(new CatalogStudioActor(9, "Alice")),
                        true,
                        2,
                        List.of(new CatalogStudioPublishedVersion(
                                11, "Summer catalog", Instant.parse("2026-08-02T10:00:00Z"))))
                .compose()
                .get();

        assertHeader(payload, Outgoing.CatalogStudioSessionComposer);
        assertEquals(11, payload.readInt());
        assertEquals(12, payload.readInt());
        assertEquals(7, payload.readInt());
        assertEquals("2026-08-02T10:00:00Z", readString(payload));
        assertEquals("2026-08-02T10:05:00Z", readString(payload));
        assertEquals(3, payload.readInt());
        assertEquals(1, payload.readInt());
        assertEquals(9, payload.readInt());
        assertEquals("Alice", readString(payload));
        assertTrue(payload.readBoolean());
        assertEquals(2, payload.readInt());
        assertEquals(1, payload.readInt());
        assertEquals(11, payload.readInt());
        assertEquals("Summer catalog", readString(payload));
        assertEquals("2026-08-02T10:00:00Z", readString(payload));
        assertEquals(2, payload.readInt());
        assertEquals("GZIP_BASE64_JSON", readString(payload));
        assertEquals(0, payload.readInt());
        assertFalse(payload.isReadable());
    }

    @Test
    void sessionPayloadKeepsEveryCompressedPageChunkReadableByRenderer() throws IOException {
        List<CatalogPageSnapshot> pages = new ArrayList<>();
        Random random = new Random(42);
        String lastText = "";
        for (int pageId = 1; pageId <= 700; pageId++) {
            byte[] content = new byte[1_500];
            random.nextBytes(content);
            lastText = Base64.getEncoder().encodeToString(content);
            pages.add(page(pageId, lastText));
        }

        ByteBuf payload = new CatalogStudioSessionComposer(
                        11,
                        12,
                        7,
                        Instant.parse("2026-08-02T10:00:00Z"),
                        Instant.parse("2026-08-02T10:05:00Z"),
                        3,
                        List.of(),
                        true,
                        0,
                        List.of(),
                        pages)
                .compose()
                .get();

        assertHeader(payload, Outgoing.CatalogStudioSessionComposer);
        skipSessionMetadata(payload);
        assertEquals(2, payload.readInt());
        assertEquals("GZIP_BASE64_JSON", readString(payload));
        int chunkCount = payload.readInt();
        assertTrue(chunkCount > 1);

        StringBuilder encoded = new StringBuilder();
        for (int index = 0; index < chunkCount; index++) {
            String chunk = readString(payload);
            assertTrue(chunk.getBytes(StandardCharsets.UTF_8).length <= 32_767);
            encoded.append(chunk);
        }

        byte[] compressed = Base64.getDecoder().decode(encoded.toString());
        String json;
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        CatalogPageSnapshot[] decoded = new Gson().fromJson(json, CatalogPageSnapshot[].class);
        assertEquals(700, decoded.length);
        assertEquals(lastText, decoded[699].pageTextDetails());
        assertFalse(payload.isReadable());
    }

    @Test
    void lockPayloadIncludesOwnerTokenAndExpiryForAcquireAndRenew() {
        ByteBuf payload = new CatalogStudioLockComposer(
                        Outgoing.CatalogStudioAcquireLockComposer,
                        "op-lock",
                        true,
                        "LOCK_ACQUIRED",
                        "Lock acquired",
                        12,
                        "PAGE",
                        "BUILDER",
                        44,
                        9,
                        "Alice",
                        "token-123",
                        Instant.parse("2026-08-02T10:06:30Z"))
                .compose()
                .get();

        assertHeader(payload, Outgoing.CatalogStudioAcquireLockComposer);
        assertEquals("op-lock", readString(payload));
        assertTrue(payload.readBoolean());
        assertEquals("LOCK_ACQUIRED", readString(payload));
        assertEquals("Lock acquired", readString(payload));
        assertEquals(12, payload.readInt());
        assertEquals("PAGE", readString(payload));
        assertEquals("BUILDER", readString(payload));
        assertEquals(44, payload.readInt());
        assertEquals(9, payload.readInt());
        assertEquals("Alice", readString(payload));
        assertEquals("token-123", readString(payload));
        assertEquals("2026-08-02T10:06:30Z", readString(payload));
        assertFalse(payload.isReadable());
    }

    @Test
    void operationPayloadKeepsRevisionAndChangedEntityIdsInOrder() {
        ByteBuf payload = new CatalogStudioOperationComposer(
                        Outgoing.CatalogStudioPublishComposer,
                        "op-publish",
                        true,
                        "PUBLISHED",
                        "Catalog published",
                        8,
                        List.of(
                                new CatalogStudioChangedEntity("PAGE", 44),
                                new CatalogStudioChangedEntity("OFFER", 77)))
                .compose()
                .get();

        assertHeader(payload, Outgoing.CatalogStudioPublishComposer);
        assertEquals("op-publish", readString(payload));
        assertTrue(payload.readBoolean());
        assertEquals("PUBLISHED", readString(payload));
        assertEquals("Catalog published", readString(payload));
        assertEquals(8, payload.readInt());
        assertEquals(2, payload.readInt());
        assertEquals("PAGE", readString(payload));
        assertEquals(44, payload.readInt());
        assertEquals("OFFER", readString(payload));
        assertEquals(77, payload.readInt());
        assertFalse(payload.isReadable());
    }

    @Test
    void historyPayloadIncludesGroupsAndCompactEntityEntries() {
        ByteBuf payload = new CatalogStudioHistoryComposer(
                        12,
                        7,
                        1,
                        List.of(new CatalogStudioHistoryGroup(
                                91,
                                7,
                                9,
                                "Alice",
                                "Move offer",
                                "UI",
                                Instant.parse("2026-08-02T10:05:30Z"),
                                List.of(new CatalogStudioHistoryEntry("OFFER", 77, "MOVE")))))
                .compose()
                .get();

        assertHeader(payload, Outgoing.CatalogStudioHistoryComposer);
        assertEquals(12, payload.readInt());
        assertEquals(7, payload.readInt());
        assertEquals(1, payload.readInt());
        assertEquals(1, payload.readInt());
        assertEquals(91, payload.readInt());
        assertEquals(7, payload.readInt());
        assertEquals(9, payload.readInt());
        assertEquals("Alice", readString(payload));
        assertEquals("Move offer", readString(payload));
        assertEquals("UI", readString(payload));
        assertEquals("2026-08-02T10:05:30Z", readString(payload));
        assertEquals(1, payload.readInt());
        assertEquals("OFFER", readString(payload));
        assertEquals(77, payload.readInt());
        assertEquals("MOVE", readString(payload));
        assertFalse(payload.isReadable());
    }

    @Test
    void validationPayloadProvidesNavigableIssueDetails() {
        ByteBuf payload = new CatalogStudioValidationComposer(
                        "op-validation",
                        false,
                        "VALIDATION_FAILED",
                        "One issue found",
                        7,
                        true,
                        List.of(new CatalogStudioValidationIssue(
                                "PAGE_PARENT_MISSING", "PAGE", 44, "parentId", "Parent page is missing")))
                .compose()
                .get();

        assertHeader(payload, Outgoing.CatalogStudioValidationComposer);
        assertEquals("op-validation", readString(payload));
        assertFalse(payload.readBoolean());
        assertEquals("VALIDATION_FAILED", readString(payload));
        assertEquals("One issue found", readString(payload));
        assertEquals(7, payload.readInt());
        assertTrue(payload.readBoolean());
        assertEquals(1, payload.readInt());
        assertEquals("PAGE_PARENT_MISSING", readString(payload));
        assertEquals("PAGE", readString(payload));
        assertEquals(44, payload.readInt());
        assertEquals("parentId", readString(payload));
        assertEquals("Parent page is missing", readString(payload));
        assertFalse(payload.isReadable());
    }

    @Test
    void previewPayloadCarriesTypedSnapshotJson() {
        ByteBuf payload = new CatalogStudioPreviewComposer(
                        "op-preview", new CatalogDraftPreview(12, 7, List.of(), List.of()))
                .compose()
                .get();

        assertHeader(payload, Outgoing.CatalogStudioPreviewComposer);
        assertEquals("op-preview", readString(payload));
        assertEquals(7, payload.readInt());
        assertEquals("[]", readString(payload));
        assertEquals("[]", readString(payload));
        assertFalse(payload.isReadable());
    }

    @Test
    void documentResultPayloadKeepsFingerprintAndDiffCount() {
        ByteBuf payload = new CatalogStudioDocumentResultComposer(
                        "op-dry", true, "DRY_RUN_READY", "Dry-run ready", 7, "JSONC", "{}", "fingerprint", 3)
                .compose()
                .get();

        assertHeader(payload, Outgoing.CatalogStudioDocumentResultComposer);
        assertEquals("op-dry", readString(payload));
        assertTrue(payload.readBoolean());
        assertEquals("DRY_RUN_READY", readString(payload));
        assertEquals("Dry-run ready", readString(payload));
        assertEquals(7, payload.readInt());
        assertEquals("JSONC", readString(payload));
        assertEquals("{}", readString(payload));
        assertEquals("fingerprint", readString(payload));
        assertEquals(3, payload.readInt());
        assertFalse(payload.isReadable());
    }

    private static void assertHeader(ByteBuf payload, int expectedHeader) {
        payload.skipBytes(4);
        assertEquals(expectedHeader, payload.readUnsignedShort());
    }

    private static String readString(ByteBuf payload) {
        int length = payload.readUnsignedShort();
        return payload.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    private static void skipSessionMetadata(ByteBuf payload) {
        payload.skipBytes(Integer.BYTES * 3);
        readString(payload);
        readString(payload);
        payload.skipBytes(Integer.BYTES);
        int actorCount = payload.readInt();
        for (int index = 0; index < actorCount; index++) {
            payload.skipBytes(Integer.BYTES);
            readString(payload);
        }
        payload.skipBytes(1 + Integer.BYTES);
        int publishedVersionCount = payload.readInt();
        for (int index = 0; index < publishedVersionCount; index++) {
            payload.skipBytes(Integer.BYTES);
            readString(payload);
            readString(payload);
        }
    }

    private static CatalogPageSnapshot page(int pageId, String textDetails) {
        return new CatalogPageSnapshot(
                pageId,
                -1,
                "page_" + pageId,
                "Page " + pageId,
                "default_3x3",
                0,
                1,
                1,
                pageId - 1,
                true,
                true,
                false,
                "NORMAL",
                false,
                "",
                "",
                "",
                "",
                "",
                textDetails,
                "",
                0,
                "");
    }
}
