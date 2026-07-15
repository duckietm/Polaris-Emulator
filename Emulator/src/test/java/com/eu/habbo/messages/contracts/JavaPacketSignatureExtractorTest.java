package com.eu.habbo.messages.contracts;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaPacketSignatureExtractorTest {
    private final JavaPacketSignatureExtractor extractor = new JavaPacketSignatureExtractor();

    @Test
    void rejectsIncomingFieldsDelegatedToAnExternalConstructor() throws IOException {
        ExtractionResult result = extractor.extract(
                fixture("DelegatedIncomingFixture.java"), JavaPacketSide.INCOMING, "handle");

        assertTrue(result.unsupportedReason().orElseThrow().contains("external constructor"));
    }

    @Test
    void rejectsOutgoingFieldsDelegatedToAnExternalSerializer() throws IOException {
        ExtractionResult result = extractor.extract(
                fixture("DelegatedOutgoingFixture.java"), JavaPacketSide.OUTGOING, "composeInternal");

        assertTrue(result.unsupportedReason().orElseThrow().contains("external serializer"));
    }

    @Test
    void extractsIncomingReadsAndExpandsLocalHelpersInCallOrder() throws Exception {
        ExtractionResult result = extractor.extract(
                fixture("IncomingFixture.java"),
                JavaPacketSide.INCOMING,
                "handle");

        assertFalse(result.unsupportedReason().isPresent());
        assertEquals(List.of("int", "string", "short", "boolean"), scalarTypes(result.fields()));
    }

    @Test
    void extractsOutgoingWritesAndExpandsLocalHelpersInCallOrder() throws Exception {
        ExtractionResult result = extractor.extract(
                fixture("OutgoingFixture.java"),
                JavaPacketSide.OUTGOING,
                "composeInternal");

        assertFalse(result.unsupportedReason().isPresent());
        assertEquals(List.of("int", "string", "short", "boolean"), scalarTypes(result.fields()));
    }

    @Test
    void collapsesEquivalentTryAndCatchWritesIntoOneWireField() throws Exception {
        ExtractionResult result = extractor.extract(
                fixture("EquivalentTryCatchOutgoingFixture.java"),
                JavaPacketSide.OUTGOING,
                "composeInternal");

        assertFalse(result.unsupportedReason().isPresent());
        assertEquals(List.of("int", "int", "string"), scalarTypes(result.fields()));
    }

    @Test
    void verifierReportsFirstOrderMismatchWithContext() {
        List<WireSchema> expected = List.of(
                new ScalarSchema("int", "id"),
                new ScalarSchema("string", "name"));
        List<WireSchema> observed = List.of(
                new ScalarSchema("string", "name"),
                new ScalarSchema("int", "id"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PacketContractVerifier.verify(
                        expected,
                        observed,
                        new PacketContractContext("SEND_FIXTURE", "client_to_server", "Fixture.java")));

        assertTrue(error.getMessage().contains("SEND_FIXTURE"));
        assertTrue(error.getMessage().contains("fields[0]"));
        assertTrue(error.getMessage().contains("expected int"));
        assertTrue(error.getMessage().contains("observed string"));
    }

    @Test
    void verifierDistinguishesShortFromIntAtSamePosition() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PacketContractVerifier.verify(
                        List.of(new ScalarSchema("int", "id")),
                        List.of(new ScalarSchema("short", "id")),
                        new PacketContractContext("TYPE_FIXTURE", "server_to_client", "Fixture.java")));

        assertTrue(error.getMessage().contains("expected int"));
        assertTrue(error.getMessage().contains("observed short"));
    }

    private static Path fixture(String name) {
        return Path.of("src", "test", "resources", "packet-contracts", "java", name);
    }

    private static List<String> scalarTypes(List<WireSchema> fields) {
        return fields.stream().map(field -> ((ScalarSchema) field).type()).toList();
    }
}
