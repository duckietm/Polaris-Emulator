package com.eu.habbo.messages.outgoing.catalog.catalogadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeEntry;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeGroup;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeOperation;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogChangeSource;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogEntityType;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogPageSnapshot;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogSmartSaveResult;
import com.eu.habbo.habbohotel.catalog.versioning.CatalogStudioDocumentWireCodec;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioActor;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioDocumentResultComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioHistoryComposer;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioHistoryEntry;
import com.eu.habbo.messages.outgoing.catalog.catalogadmin.studio.CatalogStudioHistoryGroup;
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
    void documentResultPayloadKeepsFingerprintAndDiffCount() {
        String document = "UPDATE catalog_pages SET caption = 'Shop' WHERE id = 1;";
        ByteBuf payload = new CatalogStudioDocumentResultComposer(
                        "op-dry", true, "DRY_RUN_READY", "Dry-run ready", 7, "SQL", document, "fingerprint", 3)
                .compose()
                .get();

        assertHeader(payload, Outgoing.CatalogStudioDocumentResultComposer);
        assertEquals("op-dry", readString(payload));
        assertTrue(payload.readBoolean());
        assertEquals("DRY_RUN_READY", readString(payload));
        assertEquals("Dry-run ready", readString(payload));
        assertEquals(7, payload.readInt());
        assertEquals("SQL", readString(payload));
        String encoding = readString(payload);
        int chunkCount = payload.readInt();
        java.util.List<String> chunks = new java.util.ArrayList<>(chunkCount);
        for (int index = 0; index < chunkCount; index++) chunks.add(readString(payload));
        assertEquals(document, CatalogStudioDocumentWireCodec.decode(encoding, chunks));
        assertEquals("fingerprint", readString(payload));
        assertEquals(3, payload.readInt());
        assertEquals(0, payload.readInt());
        assertFalse(payload.isReadable());
    }

    @Test
    void catalogAdminResultKeepsLegacyFieldsAndAppendsTheVersionedSmartSavePayload() {
        CatalogAdminSmartSavePayload smartSave = new CatalogAdminSmartSavePayload(
                "save-page-1",
                "savePage",
                "SAVED",
                12,
                8,
                "PAGE",
                "NORMAL",
                44,
                "{\"pageId\":44}",
                "{\"id\":91}",
                "{}",
                13);
        ByteBuf payload = new CatalogAdminResultComposer(true, "Page saved", smartSave)
                .compose()
                .get();

        assertHeader(payload, Outgoing.CatalogAdminResultComposer);
        assertTrue(payload.readBoolean());
        assertEquals("Page saved", readString(payload));
        assertEquals(1, payload.readInt());
        assertEquals("save-page-1", readString(payload));
        assertEquals("savePage", readString(payload));
        assertEquals("SAVED", readString(payload));
        assertEquals(12, payload.readInt());
        assertEquals(8, payload.readInt());
        assertEquals("PAGE", readString(payload));
        assertEquals("NORMAL", readString(payload));
        assertEquals(44, payload.readInt());
        assertEquals("{\"pageId\":44}", readString(payload));
        assertEquals("{\"id\":91}", readString(payload));
        assertEquals("{}", readString(payload));
        assertEquals(13, payload.readInt());
        assertFalse(payload.isReadable());
    }

    @Test
    void smartSavePayloadFactoryUsesStableHistoryJsonAndAllowsCreateFailures() {
        CatalogChangeEntry entry = new CatalogChangeEntry(
                1,
                CatalogEntityType.PAGE,
                com.eu.habbo.habbohotel.catalog.CatalogPageType.NORMAL,
                44,
                CatalogChangeOperation.UPDATE,
                "{}",
                "{\"pageId\":44}");
        CatalogChangeGroup group = new CatalogChangeGroup(
                91,
                12,
                8,
                9,
                "Edit page",
                CatalogChangeSource.UI,
                Instant.parse("2026-08-02T10:05:30Z"),
                List.of(entry));
        CatalogSmartSaveResult result = new CatalogSmartSaveResult(
                false,
                "save-page-1",
                12,
                8,
                CatalogEntityType.PAGE,
                com.eu.habbo.habbohotel.catalog.CatalogPageType.NORMAL,
                44,
                CatalogChangeOperation.UPDATE,
                group,
                "{\"pageId\":44}",
                13);

        CatalogAdminSmartSavePayload success =
                CatalogAdminSmartSavePayload.success("savePage", "SAVED", result, "Alice", new Gson());
        CatalogAdminSmartSavePayload failure = CatalogAdminSmartSavePayload.failure(
                "create-page-1",
                "createPage",
                "VALIDATION_FAILED",
                12,
                7,
                "PAGE",
                "NORMAL",
                0,
                "{\"caption\":\"Page caption is required\"}");

        assertTrue(success.historyGroupJson().contains("\"actorName\":\"Alice\""));
        assertTrue(success.historyGroupJson().contains("\"createdAt\":\"2026-08-02T10:05:30Z\""));
        assertEquals(0, failure.entityId());
        assertEquals("{\"caption\":\"Page caption is required\"}", failure.fieldErrorsJson());
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
