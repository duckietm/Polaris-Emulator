package com.eu.habbo.messages.incoming.catalog.catalogadmin.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogStudioRequestParserTest {

    @Test
    void parsesRevisionAndUndoRequestsInFrozenFieldOrder() {
        CatalogStudioRevisionRequest publish = CatalogStudioRequestParser.parseRevision(
                message(buffer().string("op-publish").integer(12).integer(7)));
        CatalogStudioUndoRequest undo = CatalogStudioRequestParser.parseUndo(
                message(buffer().string("op-undo").integer(12).integer(7).integer(91)));

        assertEquals("op-publish", publish.operationId());
        assertEquals(12, publish.draftVersionId());
        assertEquals(7, publish.expectedRevision());
        assertEquals(91, undo.groupId());
    }

    @Test
    void clampsHistoryPaginationAtTheProtocolBoundary() {
        CatalogStudioHistoryRequest request = CatalogStudioRequestParser.parseHistory(
                message(buffer().integer(12).integer(-4).integer(5000)));

        assertEquals(12, request.draftVersionId());
        assertEquals(0, request.offset());
        assertEquals(100, request.limit());
    }

    @Test
    void parsesTheLiveMutationEnvelopeWithoutAStoredLock() {
        CatalogStudioMutationEnvelope envelope = CatalogStudioRequestParser.parseMutationEnvelope(
                message(buffer().integer(1).integer(7).string("").string("Edit page")));

        assertEquals(1, envelope.draftVersionId());
        assertEquals(7, envelope.expectedRevision());
        assertEquals(new UUID(0, 0), envelope.lockToken());
        assertEquals("Edit page", envelope.summary());
        assertEquals("", envelope.operationId());
    }

    @Test
    void parsesAnOptionalSmartSaveOperationIdWithoutChangingTheLegacyEnvelope() {
        UUID token = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        CatalogStudioMutationEnvelope envelope =
                CatalogStudioRequestParser.parseMutationEnvelope(message(buffer().integer(12)
                        .integer(7)
                        .string(token.toString())
                        .string("Edit page")
                        .string("save-page-1")));

        assertEquals("save-page-1", envelope.operationId());
    }

    private static ClientMessage message(BufferBuilder builder) {
        return new ClientMessage(0, builder.value);
    }

    private static BufferBuilder buffer() {
        return new BufferBuilder();
    }

    private static final class BufferBuilder {
        private final ByteBuf value = Unpooled.buffer();

        private BufferBuilder integer(int value) {
            this.value.writeInt(value);
            return this;
        }

        private BufferBuilder string(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            this.value.writeShort(bytes.length);
            this.value.writeBytes(bytes);
            return this;
        }
    }
}
