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
    private static final Set<String> SCALAR_TYPES = Set.of(
            "byte", "short", "int", "long", "boolean", "string", "bytes");
    private static final Set<String> VAGUE_REASONS = Set.of(
            "complex packet", "dynamic packet", "unsupported packet", "legacy packet");

    PacketContractManifest load(Path path) throws IOException {
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    PacketContractManifest parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int schemaVersion = requiredInt(root, "schemaVersion", "manifest");
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Expected packet contract schemaVersion 1 but found " + schemaVersion);
        }

        List<PacketContract> contracts = parseContracts(requiredArray(root, "contracts", "manifest"));
        List<UnpairedPacket> unpaired = parseUnpaired(requiredArray(root, "unpaired", "manifest"));
        List<PacketExemption> exemptions = parseExemptions(requiredArray(root, "exemptions", "manifest"));
        validateUniqueClassifications(contracts, unpaired, exemptions);
        return new PacketContractManifest(schemaVersion, contracts, unpaired, exemptions);
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
                        countType,
                        parseSchemas(requiredArray(object, "item", context), context + ".list.item"));
            }
            case "optional" -> new OptionalSchema(
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
        int header = requiredInt(object, "header", context);
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
            List<PacketContract> contracts,
            List<UnpairedPacket> unpaired,
            List<PacketExemption> exemptions) {
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
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
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
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(context + " requires integer " + key);
        }
        try {
            return value.getAsInt();
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(context + " requires integer " + key, error);
        }
    }
}
