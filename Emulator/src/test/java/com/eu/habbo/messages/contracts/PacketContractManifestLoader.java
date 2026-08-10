package com.eu.habbo.messages.contracts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PacketContractManifestLoader {
    private static final Set<String> DIRECTIONS = Set.of("client_to_server", "server_to_client");
    private static final Set<String> SIDES = Set.of("java", "typescript");
    private static final Set<String> ORIGINS = Set.of("official", "custom");
    private static final Set<String> STABILITIES = Set.of("stable", "experimental", "deprecated");
    private static final Set<String> SCALAR_TYPES =
            Set.of("byte", "short", "int", "long", "boolean", "string", "bytes");
    private static final Set<String> VAGUE_REASONS =
            Set.of("complex packet", "dynamic packet", "unsupported packet", "legacy packet");

    PacketContractManifest load(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    PacketContractManifest parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int schemaVersion = requiredInt(root, "schemaVersion", "manifest");
        if (schemaVersion != 2) {
            throw new IllegalArgumentException("Expected packet contract schemaVersion 2 but found " + schemaVersion);
        }

        PacketRegistryPolicy registry = parseRegistry(requiredObject(root, "registry", "manifest"));
        List<PacketContract> contracts = parseContracts(requiredArray(root, "contracts", "manifest"));
        List<UnpairedPacket> unpaired = parseUnpaired(requiredArray(root, "unpaired", "manifest"));
        List<PacketExemption> exemptions = parseExemptions(requiredArray(root, "exemptions", "manifest"));
        validateUniqueClassifications(contracts, unpaired, exemptions);
        validateAliasClassifications(registry, contracts, unpaired, exemptions);
        return new PacketContractManifest(schemaVersion, registry, contracts, unpaired, exemptions);
    }

    private static PacketRegistryPolicy parseRegistry(JsonObject object) {
        JsonObject defaultsObject = requiredObject(object, "defaults", "registry");
        PacketRegistryDefaults defaults = new PacketRegistryDefaults(
                origin(defaultsObject, "registry defaults"), stability(defaultsObject, "registry defaults"));

        List<PacketHeaderRange> ranges = new ArrayList<>();
        for (JsonElement value : requiredArray(object, "ranges", "registry")) {
            JsonObject range = value.getAsJsonObject();
            String name = requiredString(range, "name", "registry range");
            int start = positiveInt(range, "start", "registry range " + name);
            int end = positiveInt(range, "end", "registry range " + name);
            if (end < start) throw new IllegalArgumentException("registry range " + name + " ends before it starts");
            ranges.add(new PacketHeaderRange(
                    name,
                    direction(range, "registry range " + name),
                    start,
                    end,
                    origin(range, "registry range " + name),
                    requiredString(range, "feature", "registry range " + name),
                    stability(range, "registry range " + name)));
        }
        validateRanges(ranges);

        List<PacketAlias> aliases = new ArrayList<>();
        for (JsonElement value : requiredArray(object, "aliases", "registry")) {
            JsonObject alias = value.getAsJsonObject();
            String canonical = requiredString(alias, "canonical", "packet alias");
            List<String> symbols = stringList(
                    requiredArray(alias, "aliases", "packet alias " + canonical), "packet alias " + canonical);
            if (symbols.isEmpty())
                throw new IllegalArgumentException("packet alias " + canonical + " requires aliases");
            if (symbols.contains(canonical) || new HashSet<>(symbols).size() != symbols.size()) {
                throw new IllegalArgumentException("packet alias " + canonical + " contains duplicate symbols");
            }
            aliases.add(new PacketAlias(
                    side(alias, "packet alias " + canonical),
                    direction(alias, "packet alias " + canonical),
                    positiveHeader(alias, "packet alias " + canonical),
                    canonical,
                    symbols,
                    concreteReason(alias, "packet alias " + canonical)));
        }

        List<UnsupportedPacket> unsupported = new ArrayList<>();
        Set<String> unsupportedKeys = new HashSet<>();
        for (JsonElement value : requiredArray(object, "unsupported", "registry")) {
            JsonObject packet = value.getAsJsonObject();
            String symbol = requiredString(packet, "symbol", "unsupported packet");
            UnsupportedPacket entry = new UnsupportedPacket(
                    side(packet, "unsupported packet " + symbol),
                    direction(packet, "unsupported packet " + symbol),
                    symbol,
                    concreteReason(packet, "unsupported packet " + symbol));
            String key = entry.side() + ':' + entry.direction() + ':' + entry.symbol();
            if (!unsupportedKeys.add(key)) throw new IllegalArgumentException("duplicate unsupported packet " + key);
            unsupported.add(entry);
        }
        return new PacketRegistryPolicy(defaults, ranges, aliases, unsupported);
    }

    private static void validateRanges(List<PacketHeaderRange> ranges) {
        Set<String> names = new HashSet<>();
        for (PacketHeaderRange range : ranges) {
            if (!names.add(range.name()))
                throw new IllegalArgumentException("duplicate registry range " + range.name());
        }
        for (int left = 0; left < ranges.size(); left++) {
            for (int right = left + 1; right < ranges.size(); right++) {
                PacketHeaderRange first = ranges.get(left);
                PacketHeaderRange second = ranges.get(right);
                if (!first.direction().equals(second.direction())) continue;
                if (first.start() <= second.end() && second.start() <= first.end()) {
                    throw new IllegalArgumentException("overlapping " + first.direction() + " registry ranges "
                            + first.name() + " and " + second.name());
                }
            }
        }
    }

    private static void validateAliasClassifications(
            PacketRegistryPolicy registry,
            List<PacketContract> contracts,
            List<UnpairedPacket> unpaired,
            List<PacketExemption> exemptions) {
        Set<String> classified = new HashSet<>();
        contracts.forEach(packet -> classified.add(packet.direction() + ':' + packet.header()));
        unpaired.forEach(packet -> classified.add(packet.direction() + ':' + packet.header()));
        exemptions.forEach(packet -> classified.add(packet.direction() + ':' + packet.header()));
        for (PacketAlias alias : registry.aliases()) {
            String key = alias.direction() + ':' + alias.header();
            if (!classified.contains(key)) {
                throw new IllegalArgumentException("alias header " + key + " is not classified");
            }
        }
    }

    private static String origin(JsonObject object, String context) {
        String origin = requiredString(object, "origin", context);
        if (!ORIGINS.contains(origin)) throw new IllegalArgumentException(context + " has unknown origin " + origin);
        return origin;
    }

    private static String stability(JsonObject object, String context) {
        String stability = requiredString(object, "stability", context);
        if (!STABILITIES.contains(stability)) {
            throw new IllegalArgumentException(context + " has unknown stability " + stability);
        }
        return stability;
    }

    private static String side(JsonObject object, String context) {
        String side = requiredString(object, "side", context);
        if (!SIDES.contains(side)) throw new IllegalArgumentException(context + " has unknown side " + side);
        return side;
    }

    private static List<String> stringList(JsonArray values, String context) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            JsonElement value = values.get(index);
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(context + " requires string at index " + index);
            }
            String symbol = value.getAsString().strip();
            if (symbol.isEmpty()) throw new IllegalArgumentException(context + " requires non-empty symbol");
            result.add(symbol);
        }
        return result;
    }

    private static List<PacketContract> parseContracts(JsonArray values) {
        List<PacketContract> result = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject object = value.getAsJsonObject();
            String name = requiredString(object, "name", "contract");
            String direction = direction(object, "contract " + name);
            int header = positiveHeader(object, "contract " + name);
            PacketEndpoint java = endpoint(object, "java", "contract " + name);
            PacketEndpoint typescript = endpoint(object, "typescript", "contract " + name);
            result.add(new PacketContract(
                    name,
                    direction,
                    header,
                    java,
                    typescript,
                    parseSchemas(requiredArray(object, "fields", "contract " + name), "contract " + name)));
        }
        return result;
    }

    private static List<UnpairedPacket> parseUnpaired(JsonArray values) {
        List<UnpairedPacket> result = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject object = value.getAsJsonObject();
            String direction = direction(object, "unpaired packet");
            String side = requiredString(object, "side", "unpaired packet");
            if (!SIDES.contains(side)) throw new IllegalArgumentException("Unknown unpaired side: " + side);
            String symbol = requiredString(object, "symbol", "unpaired packet");
            result.add(new UnpairedPacket(
                    direction,
                    side,
                    positiveHeader(object, "unpaired packet " + symbol),
                    symbol,
                    requiredString(object, "path", "unpaired packet " + symbol),
                    concreteReason(object, "unpaired packet " + symbol)));
        }
        return result;
    }

    private static List<PacketExemption> parseExemptions(JsonArray values) {
        List<PacketExemption> result = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject object = value.getAsJsonObject();
            String name = requiredString(object, "name", "exemption");
            result.add(new PacketExemption(
                    name,
                    direction(object, "exemption " + name),
                    positiveHeader(object, "exemption " + name),
                    endpoint(object, "java", "exemption " + name),
                    endpoint(object, "typescript", "exemption " + name),
                    concreteReason(object, "exemption " + name)));
        }
        return result;
    }

    private static List<WireSchema> parseSchemas(JsonArray values, String context) {
        List<WireSchema> result = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            result.add(parseSchema(values.get(index).getAsJsonObject(), context + " fields[" + index + "]"));
        }
        return result;
    }

    private static WireSchema parseSchema(JsonObject object, String context) {
        String kind = requiredString(object, "kind", context);
        return switch (kind) {
            case "scalar" -> {
                String type = requiredString(object, "type", context);
                validateScalarType(type, context);
                yield new ScalarSchema(type, optionalString(object, "name"));
            }
            case "list" -> {
                String countType = requiredString(object, "countType", context);
                validateScalarType(countType, context + " countType");
                yield new ListSchema(
                        countType, parseSchemas(requiredArray(object, "item", context), context + ".list.item"));
            }
            case "optional" ->
                new OptionalSchema(
                        requiredString(object, "controller", context),
                        parseSchemas(requiredArray(object, "fields", context), context + ".optional.fields"));
            case "variant" -> {
                String discriminator = requiredString(object, "discriminator", context);
                JsonObject branchesObject = requiredObject(object, "branches", context);
                if (branchesObject.isEmpty()) {
                    throw new IllegalArgumentException(context + " variant branches must not be empty");
                }
                Map<String, List<WireSchema>> branches = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> branch : branchesObject.entrySet()) {
                    branches.put(
                            branch.getKey(),
                            parseSchemas(branch.getValue().getAsJsonArray(), context + ".variant." + branch.getKey()));
                }
                yield new VariantSchema(discriminator, branches);
            }
            default -> throw new IllegalArgumentException(context + " has unknown schema kind " + kind);
        };
    }

    private static void validateScalarType(String type, String context) {
        if (!SCALAR_TYPES.contains(type)) {
            throw new IllegalArgumentException(context + " has unknown scalar type " + type);
        }
    }

    private static PacketEndpoint endpoint(JsonObject object, String key, String context) {
        JsonObject endpoint = requiredObject(object, key, context);
        return new PacketEndpoint(
                requiredString(endpoint, "symbol", context + " " + key),
                requiredString(endpoint, "className", context + " " + key),
                requiredString(endpoint, "path", context + " " + key));
    }

    private static String direction(JsonObject object, String context) {
        String direction = requiredString(object, "direction", context);
        if (!DIRECTIONS.contains(direction)) {
            throw new IllegalArgumentException(context + " has unknown direction " + direction);
        }
        return direction;
    }

    private static int positiveHeader(JsonObject object, String context) {
        int header = positiveInt(object, "header", context);
        return header;
    }

    private static int positiveInt(JsonObject object, String key, String context) {
        int header = requiredInt(object, key, context);
        if (header <= 0) throw new IllegalArgumentException(context + " header must be positive");
        return header;
    }

    private static String concreteReason(JsonObject object, String context) {
        String reason = requiredString(object, "reason", context).strip();
        if (reason.length() < 20 || VAGUE_REASONS.contains(reason.toLowerCase())) {
            throw new IllegalArgumentException(context + " requires a concrete exemption reason");
        }
        return reason;
    }

    private static void validateUniqueClassifications(
            List<PacketContract> contracts, List<UnpairedPacket> unpaired, List<PacketExemption> exemptions) {
        Set<String> seen = new HashSet<>();
        for (PacketContract contract : contracts) addClassification(seen, contract.direction(), contract.header());
        for (UnpairedPacket packet : unpaired) addClassification(seen, packet.direction(), packet.header());
        for (PacketExemption exemption : exemptions) {
            addClassification(seen, exemption.direction(), exemption.header());
        }
    }

    private static void addClassification(Set<String> seen, String direction, int header) {
        String key = direction + ":" + header;
        if (!seen.add(key)) {
            throw new IllegalArgumentException("Packet " + key + " is classified more than once");
        }
    }

    private static JsonArray requiredArray(JsonObject object, String key, String context) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(context + " requires array " + key);
        }
        return value.getAsJsonArray();
    }

    private static JsonObject requiredObject(JsonObject object, String key, String context) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(context + " requires object " + key);
        }
        return value.getAsJsonObject();
    }

    private static String requiredString(JsonObject object, String key, String context) {
        JsonElement value = object.get(key);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(context + " requires string " + key);
        }
        String result = value.getAsString().strip();
        if (result.isEmpty()) throw new IllegalArgumentException(context + " requires non-empty " + key);
        return result;
    }

    private static String optionalString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString().strip();
    }

    private static int requiredInt(JsonObject object, String key, String context) {
        JsonElement value = object.get(key);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(context + " requires integer " + key);
        }
        try {
            return value.getAsInt();
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(context + " requires integer " + key, error);
        }
    }
}
