package com.eu.habbo.messages.contracts;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class PacketContractCatalogTest {
    private final Path repositoryRoot = Path.of("..").toAbsolutePath().normalize();

    @Test
    void verifiesEveryNonExemptJavaPacketSignature() throws Exception {
        PacketContractManifest manifest = new PacketContractManifestLoader().load(
                repositoryRoot.resolve("protocol/packet-field-contracts.json"));
        JavaPacketSignatureExtractor extractor = new JavaPacketSignatureExtractor();
        for (PacketContract contract : manifest.contracts()) {
            JavaPacketSide side = contract.direction().equals("client_to_server")
                    ? JavaPacketSide.INCOMING
                    : JavaPacketSide.OUTGOING;
            ExtractionResult observed = extractor.extract(
                    repositoryRoot.resolve(contract.java().path()),
                    side,
                    side == JavaPacketSide.INCOMING ? "handle" : "composeInternal");
            if (observed.unsupportedReason().isPresent()) {
                throw new AssertionError(contract.name() + " became unsupported: " + observed.unsupportedReason().get());
            }
            PacketContractVerifier.verify(
                    contract.fields(),
                    observed.fields(),
                    new PacketContractContext(contract.name(), contract.direction(), contract.java().path()));
        }
    }
}
