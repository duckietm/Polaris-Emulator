package polaris.messages.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JavaPacketSignatureExtractorTest {
    private final JavaPacketSignatureExtractor extractor = new JavaPacketSignatureExtractor();

    @Test
    void rejectsIncomingFieldsDelegatedToAnExternalConstructor() throws IOException {
        ExtractionResult result =
                extractor.extract(fixture("DelegatedIncomingFixture.java"), JavaPacketSide.INCOMING, "handle");

        assertTrue(result.unsupportedReason().orElseThrow().contains("external constructor"));
    }

    @Test
    void rejectsIncomingFieldsDelegatedToAnExternalReaderMethod() throws IOException {
        ExtractionResult result =
                extractor.extract(fixture("DelegatedIncomingMethodFixture.java"), JavaPacketSide.INCOMING, "handle");

        assertTrue(result.unsupportedReason().orElseThrow().contains("external reader read"));
    }

    @Test
    void rejectsOutgoingFieldsDelegatedToAnExternalSerializer() throws IOException {
        ExtractionResult result =
                extractor.extract(fixture("DelegatedOutgoingFixture.java"), JavaPacketSide.OUTGOING, "composeInternal");

        assertTrue(result.unsupportedReason().orElseThrow().contains("external serializer"));
    }

    @Test
    void extractsOnlyAGuardedTrailingIncomingScalarAsOptional() throws Exception {
        ExtractionResult result =
                extractor.extract(fixture("OptionalIncomingFixture.java"), JavaPacketSide.INCOMING, "handle");
        assertFalse(result.unsupportedReason().isPresent());
        assertEquals(
                List.of(
                        new ScalarSchema("int", "id"),
                        new OptionalSchema("token", List.of(new ScalarSchema("string", "token")))),
                result.fields());
        assertTrue(extractor
                .extract(fixture("OptionalIncomingFixture.java"), JavaPacketSide.INCOMING, "nonTrailing")
                .unsupportedReason()
                .isPresent());
        assertTrue(extractor
                .extract(fixture("OptionalIncomingFixture.java"), JavaPacketSide.INCOMING, "conditional")
                .unsupportedReason()
                .isPresent());
    }

    @Test
    void extractsIncomingReadsAndExpandsLocalHelpersInCallOrder() throws Exception {
        ExtractionResult result = extractor.extract(fixture("IncomingFixture.java"), JavaPacketSide.INCOMING, "handle");

        assertFalse(result.unsupportedReason().isPresent());
        assertEquals(List.of("int", "string", "short", "boolean"), scalarTypes(result.fields()));
    }

    @Test
    void extractsOutgoingWritesAndExpandsLocalHelpersInCallOrder() throws Exception {
        ExtractionResult result =
                extractor.extract(fixture("OutgoingFixture.java"), JavaPacketSide.OUTGOING, "composeInternal");

        assertFalse(result.unsupportedReason().isPresent());
        assertEquals(List.of("int", "string", "short", "boolean"), scalarTypes(result.fields()));
    }

    @Test
    void collapsesEquivalentTryAndCatchWritesIntoOneWireField() throws Exception {
        ExtractionResult result = extractor.extract(
                fixture("EquivalentTryCatchOutgoingFixture.java"), JavaPacketSide.OUTGOING, "composeInternal");

        assertFalse(result.unsupportedReason().isPresent());
        assertEquals(List.of("int", "int", "string"), scalarTypes(result.fields()));
    }

    @Test
    void extractsAllRendererDeclaredFieldsFromCompatibleRequests() throws Exception {
        assertEquals(
                List.of("int", "int", "int"),
                scalarTypes(extractor
                        .extract(
                                realSource("incoming/guilds/forums/GuildForumThreadsEvent.java"),
                                JavaPacketSide.INCOMING,
                                "handle")
                        .fields()));
        assertEquals(
                List.of("int", "boolean"),
                scalarTypes(extractor
                        .extract(realSource("incoming/rooms/pets/PetRideEvent.java"), JavaPacketSide.INCOMING, "handle")
                        .fields()));
        assertEquals(
                List.of("string", "int"),
                scalarTypes(extractor
                        .extract(
                                realSource("incoming/catalog/CheckPetNameEvent.java"),
                                JavaPacketSide.INCOMING,
                                "handle")
                        .fields()));
        assertEquals(
                List.of("int", "string", "int"),
                scalarTypes(extractor
                        .extract(
                                realSource("incoming/modtool/ModToolKickEvent.java"), JavaPacketSide.INCOMING, "handle")
                        .fields()));
        assertEquals(
                List.of("int", "boolean"),
                scalarTypes(extractor
                        .extract(
                                realSource("incoming/users/RequestUserProfileEvent.java"),
                                JavaPacketSide.INCOMING,
                                "handle")
                        .fields()));
    }

    @Test
    void extractsIssueDeletedIdentifierAsAString() throws Exception {
        ExtractionResult result = extractor.extract(
                realSource("outgoing/modtool/IssueDeletedComposer.java"), JavaPacketSide.OUTGOING, "composeInternal");

        assertFalse(result.unsupportedReason().isPresent());
        assertEquals(List.of("string"), scalarTypes(result.fields()));
    }

    @Test
    void extractsAllFieldsFromRemainingRendererRequests() throws Exception {
        assertIncomingSignature("incoming/camera/CameraPurchaseEvent.java", "string");
        assertIncomingSignature("incoming/handshake/SecureLoginEvent.java", "string", "int", "string");
        assertIncomingSignature("incoming/users/RequestUserWardrobeEvent.java", "int");
        assertIncomingSignature("incoming/helper/MySanctionStatusEvent.java", "boolean");
        assertIncomingSignature("incoming/navigator/RequestPopularRoomsEvent.java", "string", "int");
        assertIncomingSignature("incoming/navigator/RequestHighestScoreRoomsEvent.java", "int");
        assertIncomingSignature("incoming/gamecenter/GameCenterEvent.java", "int");
        assertIncomingSignature("incoming/gamecenter/GameCenterLeaveGameEvent.java", "int");
        assertIncomingSignature("incoming/rooms/RequestRoomRightsEvent.java", "int");
        assertIncomingSignature("incoming/catalog/CatalogBuyClubDiscountEvent.java", "int");
        assertIncomingSignature("incoming/rooms/RoomVoteEvent.java", "int");
        assertIncomingSignature("incoming/users/GetIgnoredUsersEvent.java");
    }

    @Test
    void verifierReportsFirstOrderMismatchWithContext() {
        List<WireSchema> expected = List.of(new ScalarSchema("int", "id"), new ScalarSchema("string", "name"));
        List<WireSchema> observed = List.of(new ScalarSchema("string", "name"), new ScalarSchema("int", "id"));

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

    @Test
    void verifierAllowsMissingTrailingOptionalFields() {
        PacketContractVerifier.verify(
                List.of(
                        new ScalarSchema("int", "id"),
                        new OptionalSchema("bytesAvailable", List.of(new ScalarSchema("string", "figure")))),
                List.of(new ScalarSchema("int", "id")),
                new PacketContractContext("OPTIONAL_FIXTURE", "server_to_client", "Fixture.java"));
    }

    @Test
    void extractsAListAfterItsSizeCounter() throws Exception {
        assertEquals("list<int>[int,string],boolean", outgoingShape("ListOutgoingFixture.java", "composeInternal"));
    }

    @Test
    void extractsTwoConsecutiveListsInOrder() throws Exception {
        assertEquals("list<int>[int],list<int>[string]", outgoingShape("ListOutgoingFixture.java", "twoLists"));
    }

    @Test
    void extractsANestedListInsideTheItem() throws Exception {
        assertEquals("list<int>[string,list<int>[int]]", outgoingShape("ListOutgoingFixture.java", "nested"));
    }

    @Test
    void extractsAShortCounter() throws Exception {
        assertEquals("list<short>[int]", outgoingShape("ListOutgoingFixture.java", "shortCount"));
    }

    @Test
    void extractsAnIndexedForOverSize() throws Exception {
        assertEquals("list<int>[int]", outgoingShape("ListOutgoingFixture.java", "indexed"));
    }

    @Test
    void extractsAListWhoseItemIsWrittenByALocalHelper() throws Exception {
        assertEquals("list<int>[int,string]", outgoingShape("ListOutgoingFixture.java", "helperBody"));
    }

    @Test
    void rejectsListsThatAreNotDescribedExactly() throws Exception {
        for (String method : List.of(
                "countMismatch", "otherIterable", "conditionalBody", "breakBody", "detachedCounter", "emptyItem")) {
            ExtractionResult result =
                    extractor.extract(fixture("ListOutgoingFixture.java"), JavaPacketSide.OUTGOING, method);
            assertTrue(result.unsupportedReason().isPresent(), method);
        }
    }

    @Test
    void extractsAnIncomingListAfterItsCount() throws Exception {
        assertEquals("list<int>[int,string],boolean", incomingShape("ListIncomingFixture.java", "handle"));
    }

    @Test
    void extractsAnIncomingShortCount() throws Exception {
        assertEquals("list<short>[int]", incomingShape("ListIncomingFixture.java", "shortCount"));
    }

    @Test
    void extractsANestedIncomingList() throws Exception {
        assertEquals("list<int>[string,list<int>[int]]", incomingShape("ListIncomingFixture.java", "nested"));
    }

    @Test
    void extractsADecrementingIncomingLoop() throws Exception {
        assertEquals("list<int>[int]", incomingShape("ListIncomingFixture.java", "decrementing"));
    }

    @Test
    void acceptsAGuardOnTheCountWithoutReads() throws Exception {
        assertEquals("list<int>[int,string],boolean", incomingShape("ListIncomingFixture.java", "countUsedBefore"));
    }

    @Test
    void rejectsIncomingListsThatAreNotDescribedExactly() throws Exception {
        for (String method : List.of("countFromField", "conditionalRead", "countChangedInBody", "guardThatReads")) {
            ExtractionResult result =
                    extractor.extract(fixture("ListIncomingFixture.java"), JavaPacketSide.INCOMING, method);
            assertTrue(result.unsupportedReason().isPresent(), method);
        }
    }

    @Test
    void followsComposerSerializersIntoOtherClasses() throws Exception {
        assertEquals(
                "int,int,string,int,boolean",
                outgoingShape("delegations/SerializerComposerFixture.java", "composeInternal"));
        assertEquals(
                "list<int>[int,string,int]",
                outgoingShape("delegations/SerializerComposerFixture.java", "forEachList"));
        assertEquals(
                "int,short,string", outgoingShape("delegations/SerializerComposerFixture.java", "staticSerializer"));
    }

    @Test
    void keepsSerializersThatCannotBeResolvedExactlyUnsupported() throws Exception {
        for (String method : List.of(
                "abstractReceiver",
                "overriddenReceiver",
                "overloadedSerializer",
                "recursiveSerializer",
                "conditionalSerializer",
                "untypedReceiver")) {
            ExtractionResult result = extractor.extract(
                    fixture("delegations/SerializerComposerFixture.java"), JavaPacketSide.OUTGOING, method);
            assertTrue(result.unsupportedReason().isPresent(), method);
        }
    }

    @Test
    void keepsDelegatesThatCannotBeDescribedExactlyUnsupported() throws Exception {
        Map<String, JavaPacketSide> methods = Map.of(
                "ternaryDelegate", JavaPacketSide.OUTGOING,
                "lambdaDelegate", JavaPacketSide.OUTGOING,
                "virtualHelper", JavaPacketSide.OUTGOING,
                "renameCollision", JavaPacketSide.OUTGOING,
                "shadowedDeclaration", JavaPacketSide.OUTGOING,
                "argumentRead", JavaPacketSide.INCOMING,
                "storedPacket", JavaPacketSide.INCOMING,
                "superConstructor", JavaPacketSide.INCOMING,
                "conditionalInsideDelegate", JavaPacketSide.INCOMING);
        for (Map.Entry<String, JavaPacketSide> method : methods.entrySet()) {
            ExtractionResult result = extractor.extract(
                    fixture("delegations/ReviewFalseGreensFixture.java"), method.getValue(), method.getKey());
            assertTrue(result.unsupportedReason().isPresent(), method.getKey() + " gave " + result.fields());
        }
    }

    @Test
    void followsHandlerReadersIntoOtherClasses() throws Exception {
        assertEquals("int,string,int,boolean", incomingShape("delegations/ReaderHandlerFixture.java", "handle"));
        assertEquals("int,string", incomingShape("delegations/ReaderHandlerFixture.java", "staticReader"));
        assertEquals("boolean", incomingShape("delegations/ReaderHandlerFixture.java", "instanceReader"));
        for (String method : List.of("abstractReader", "handlerPassed")) {
            ExtractionResult result = extractor.extract(
                    fixture("delegations/ReaderHandlerFixture.java"), JavaPacketSide.INCOMING, method);
            assertTrue(result.unsupportedReason().isPresent(), method);
        }
    }

    @Test
    void keepsUnknownPacketOperationsUnsupported() throws Exception {
        assertTrue(extractor
                .extract(fixture("ListOutgoingFixture.java"), JavaPacketSide.OUTGOING, "unknownAppend")
                .unsupportedReason()
                .orElseThrow()
                .contains("Unknown packet operation appendDouble"));
        assertTrue(extractor
                .extract(fixture("ListIncomingFixture.java"), JavaPacketSide.INCOMING, "unknownRead")
                .unsupportedReason()
                .orElseThrow()
                .contains("Unknown packet operation readDouble"));
    }

    @Test
    void keepsAConditionalReadInsideAListItemUnsupported() throws Exception {
        assertTrue(extractor
                .extract(fixture("ListIncomingFixture.java"), JavaPacketSide.INCOMING, "conditionalItemRead")
                .unsupportedReason()
                .orElseThrow()
                .contains("Conditional packet operation"));
    }

    @Test
    void namesAMissingRootMethodByFileNameOnly() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extract(fixture("ListOutgoingFixture.java"), JavaPacketSide.OUTGOING, "missing"));

        assertEquals("Method missing not found in ListOutgoingFixture.java", error.getMessage());
    }

    private String incomingShape(String fixtureName, String method) throws IOException {
        ExtractionResult result = extractor.extract(fixture(fixtureName), JavaPacketSide.INCOMING, method);
        assertFalse(
                result.unsupportedReason().isPresent(),
                () -> result.unsupportedReason().orElseThrow());
        return shape(result.fields());
    }

    private String outgoingShape(String fixtureName, String method) throws IOException {
        ExtractionResult result = extractor.extract(fixture(fixtureName), JavaPacketSide.OUTGOING, method);
        assertFalse(
                result.unsupportedReason().isPresent(),
                () -> result.unsupportedReason().orElseThrow());
        return shape(result.fields());
    }

    static String shape(List<WireSchema> fields) {
        return String.join(
                ",",
                fields.stream()
                        .map(field -> switch (field) {
                            case ScalarSchema scalar -> scalar.type();
                            case ListSchema list -> "list<" + list.countType() + ">[" + shape(list.item()) + "]";
                            case OptionalSchema optional -> "optional[" + shape(optional.fields()) + "]";
                            case VariantSchema variant -> "variant";
                        })
                        .toList());
    }

    private static Path fixture(String name) {
        return Path.of("src", "test", "resources", "packet-contracts", "java", name);
    }

    private static Path realSource(String relative) {
        return Path.of("src", "main", "java", "polaris", "messages").resolve(relative);
    }

    private static List<String> scalarTypes(List<WireSchema> fields) {
        return fields.stream().map(field -> ((ScalarSchema) field).type()).toList();
    }

    private void assertIncomingSignature(String relative, String... types) throws Exception {
        ExtractionResult result = extractor.extract(realSource(relative), JavaPacketSide.INCOMING, "handle");

        assertFalse(result.unsupportedReason().isPresent());
        assertEquals(List.of(types), scalarTypes(result.fields()), relative);
    }
}
