package com.eu.habbo.messages.contracts;

import java.util.List;

record PacketEndpoint(String symbol, String className, String path) {}

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

record UnpairedPacket(String direction, String side, int header, String symbol, String path, String reason) {}

record PacketExemption(
        String name, String direction, int header, PacketEndpoint java, PacketEndpoint typescript, String reason) {}

record PacketMetadata(String range, String origin, String feature, String stability) {}

record PacketRegistryDefaults(String origin, String stability) {}

record PacketHeaderRange(
        String name, String direction, int start, int end, String origin, String feature, String stability) {}

record PacketAlias(String side, String direction, int header, String canonical, List<String> aliases, String reason) {
    PacketAlias {
        aliases = List.copyOf(aliases);
    }
}

record UnsupportedPacket(String side, String direction, String symbol, String reason) {}

record PacketRegistryPolicy(
        PacketRegistryDefaults defaults,
        List<PacketHeaderRange> ranges,
        List<PacketAlias> aliases,
        List<UnsupportedPacket> unsupported) {
    PacketRegistryPolicy {
        ranges = List.copyOf(ranges);
        aliases = List.copyOf(aliases);
        unsupported = List.copyOf(unsupported);
    }

    PacketMetadata metadataFor(String direction, int header) {
        return ranges.stream()
                .filter(range ->
                        range.direction().equals(direction) && header >= range.start() && header <= range.end())
                .findFirst()
                .map(range -> new PacketMetadata(range.name(), range.origin(), range.feature(), range.stability()))
                .orElseGet(() -> new PacketMetadata("", defaults.origin(), "", defaults.stability()));
    }
}

record PacketContractManifest(
        int schemaVersion,
        PacketRegistryPolicy registry,
        List<PacketContract> contracts,
        List<UnpairedPacket> unpaired,
        List<PacketExemption> exemptions) {
    PacketContractManifest {
        contracts = List.copyOf(contracts);
        unpaired = List.copyOf(unpaired);
        exemptions = List.copyOf(exemptions);
    }
}
