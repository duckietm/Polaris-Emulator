package com.eu.habbo.messages.contracts;

import java.util.List;

record PacketEndpoint(String symbol, String className, String path) {
}

record PacketContract(
        String name,
        String direction,
        int header,
        PacketEndpoint java,
        PacketEndpoint typescript,
        List<WireSchema> fields) {
    PacketContract {
        fields = List.copyOf(fields);
    }
}

record UnpairedPacket(
        String direction,
        String side,
        int header,
        String symbol,
        String path,
        String reason) {
}

record PacketExemption(
        String name,
        String direction,
        int header,
        PacketEndpoint java,
        PacketEndpoint typescript,
        String reason) {
}

record PacketContractManifest(
        int schemaVersion,
        List<PacketContract> contracts,
        List<UnpairedPacket> unpaired,
        List<PacketExemption> exemptions) {
    PacketContractManifest {
        contracts = List.copyOf(contracts);
        unpaired = List.copyOf(unpaired);
        exemptions = List.copyOf(exemptions);
    }
}
