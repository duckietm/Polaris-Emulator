package com.eu.habbo.messages.contracts;

import com.google.gson.GsonBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record JavaPacketInventoryEntry(
        String direction,
        int header,
        String symbol,
        String className,
        String path,
        List<WireSchema> fields,
        String unsupportedReason) {
    JavaPacketInventoryEntry {
        fields = List.copyOf(fields);
    }
}

record TypeScriptPacketInventoryEntry(
        String direction,
        int header,
        String symbol,
        String className,
        String path,
        List<JsonObject> fields,
        String unsupportedReason) {
}

final class PacketContractCatalogGenerator {
    private final Path repositoryRoot;

    PacketContractCatalogGenerator(Path repositoryRoot) {
        this.repositoryRoot = repositoryRoot.toAbsolutePath().normalize();
    }

    List<JavaPacketInventoryEntry> javaInventory() throws IOException {
        Path sourceRoot = repositoryRoot.resolve("Emulator/src/main/java");
        JavaPacketRegistry registry = JavaPacketRegistry.discover(sourceRoot);
        JavaPacketSignatureExtractor extractor = new JavaPacketSignatureExtractor();
        List<JavaPacketInventoryEntry> inventory = new ArrayList<>();
        for (JavaPacketRegistry.RegisteredPacket packet : registry.active()) {
            JavaPacketSide side = packet.direction() == JavaPacketRegistry.Direction.CLIENT_TO_SERVER
                    ? JavaPacketSide.INCOMING
                    : JavaPacketSide.OUTGOING;
            String rootMethod = side == JavaPacketSide.INCOMING ? "handle" : "composeInternal";
            ExtractionResult result;
            try {
                result = extractor.extract(packet.source(), side, rootMethod);
            } catch (RuntimeException error) {
                result = ExtractionResult.unsupported(
                        "Java analyzer could not resolve " + rootMethod + ": " + error.getMessage());
            }
            String className = packet.source().getFileName().toString().replaceFirst("\\.java$", "");
            inventory.add(new JavaPacketInventoryEntry(
                    packet.direction().manifestName(),
                    packet.header(),
                    packet.symbol(),
                    className,
                    relative(packet.source()),
                    result.fields(),
                    result.unsupportedReason().orElse(null)));
        }
        return inventory.stream()
                .sorted(Comparator.comparing(JavaPacketInventoryEntry::direction)
                        .thenComparingInt(JavaPacketInventoryEntry::header)
                        .thenComparing(JavaPacketInventoryEntry::symbol))
                .toList();
    }

    void writeJavaInventory(Path destination) throws IOException {
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(javaInventory()) + "\n";
        Files.createDirectories(destination.toAbsolutePath().normalize().getParent());
        Files.writeString(destination, json, StandardCharsets.UTF_8);
    }

    String generate(Path typescriptInventoryPath) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<TypeScriptPacketInventoryEntry> typescript = gson.fromJson(
                Files.readString(typescriptInventoryPath, StandardCharsets.UTF_8),
                new TypeToken<List<TypeScriptPacketInventoryEntry>>() { }.getType());
        Map<String, JavaPacketInventoryEntry> javaByKey = new LinkedHashMap<>();
        javaInventory().forEach(packet -> javaByKey.put(key(packet.direction(), packet.header()), packet));
        Map<String, TypeScriptPacketInventoryEntry> typescriptByKey = new LinkedHashMap<>();
        typescript.forEach(packet -> typescriptByKey.put(key(packet.direction(), packet.header()), packet));

        JsonArray contracts = new JsonArray();
        JsonArray exemptions = new JsonArray();
        JsonArray unpaired = new JsonArray();
        javaByKey.values().stream()
                .filter(javaPacket -> typescriptByKey.containsKey(key(javaPacket.direction(), javaPacket.header())))
                .forEach(javaPacket -> {
                    TypeScriptPacketInventoryEntry tsPacket = typescriptByKey.get(
                            key(javaPacket.direction(), javaPacket.header()));
                    List<String> javaSignature = javaPacket.fields().stream().map(this::signature).toList();
                    List<String> tsSignature = tsPacket.fields().stream().map(this::signature).toList();
                    boolean analyzable = javaPacket.unsupportedReason() == null && tsPacket.unsupportedReason() == null;
                    if (analyzable && signaturesCompatible(javaSignature, tsSignature)) {
                        contracts.add(contract(javaPacket, tsPacket, !javaSignature.equals(tsSignature)));
                    } else {
                        exemptions.add(exemption(javaPacket, tsPacket, javaSignature, tsSignature));
                    }
                });
        javaByKey.values().stream()
                .filter(packet -> !typescriptByKey.containsKey(key(packet.direction(), packet.header())))
                .forEach(packet -> unpaired.add(unpaired(
                        packet.direction(), "java", packet.header(), packet.symbol(), packet.path(),
                        "Header is registered only by the Polaris Emulator packet registry")));
        typescriptByKey.values().stream()
                .filter(packet -> !javaByKey.containsKey(key(packet.direction(), packet.header())))
                .forEach(packet -> unpaired.add(unpaired(
                        packet.direction(), "typescript", packet.header(), packet.symbol(), packet.path(),
                        "Header is registered only by the Nitro Renderer packet registry")));

        JsonObject manifest = new JsonObject();
        manifest.addProperty("schemaVersion", 1);
        manifest.add("contracts", contracts);
        manifest.add("unpaired", unpaired);
        manifest.add("exemptions", exemptions);
        return gson.toJson(manifest) + "\n";
    }

    void writeManifest(Path typescriptInventoryPath, Path... destinations) throws IOException {
        if (!"1".equals(System.getenv("PACKET_CONTRACT_WRITE"))) {
            throw new IllegalStateException("Refusing to overwrite packet manifest without PACKET_CONTRACT_WRITE=1");
        }
        String manifest = generate(typescriptInventoryPath);
        for (Path destination : destinations) {
            Files.createDirectories(destination.toAbsolutePath().normalize().getParent());
            Files.writeString(destination, manifest, StandardCharsets.UTF_8);
        }
    }

    private JsonObject contract(
            JavaPacketInventoryEntry javaPacket,
            TypeScriptPacketInventoryEntry tsPacket,
            boolean useTypeScriptFields) {
        JsonObject result = base(javaPacket, tsPacket);
        JsonArray fields = new JsonArray();
        if (useTypeScriptFields) {
            tsPacket.fields().forEach(field -> fields.add(schemaJson(field)));
        } else {
            javaPacket.fields().forEach(field -> fields.add(schemaJson(field)));
        }
        result.add("fields", fields);
        return result;
    }

    static boolean signaturesCompatible(List<String> javaSignature, List<String> typescriptSignature) {
        if (javaSignature.equals(typescriptSignature)) return true;
        if (javaSignature.size() >= typescriptSignature.size()) return false;
        if (!javaSignature.equals(typescriptSignature.subList(0, javaSignature.size()))) return false;
        return typescriptSignature.subList(javaSignature.size(), typescriptSignature.size()).stream()
                .allMatch(field -> field.startsWith("optional<")
                        || field.startsWith("{\"type\":\"optional\""));
    }

    private JsonObject exemption(
            JavaPacketInventoryEntry javaPacket,
            TypeScriptPacketInventoryEntry tsPacket,
            List<String> javaSignature,
            List<String> tsSignature) {
        JsonObject result = base(javaPacket, tsPacket);
        List<String> reasons = new ArrayList<>();
        if (javaPacket.unsupportedReason() != null) reasons.add("Java analyzer: " + javaPacket.unsupportedReason());
        if (tsPacket.unsupportedReason() != null) reasons.add("TypeScript analyzer: " + tsPacket.unsupportedReason());
        if (reasons.isEmpty()) {
            reasons.add("Pre-existing wire mismatch requires runtime protocol decision: Java "
                    + javaSignature + " versus TypeScript " + tsSignature);
        }
        result.addProperty("reason", String.join("; ", reasons));
        return result;
    }

    private JsonObject base(JavaPacketInventoryEntry javaPacket, TypeScriptPacketInventoryEntry tsPacket) {
        JsonObject result = new JsonObject();
        result.addProperty("name", tsPacket.symbol());
        result.addProperty("direction", javaPacket.direction());
        result.addProperty("header", javaPacket.header());
        result.add("java", endpoint(javaPacket.symbol(), javaPacket.className(), javaPacket.path()));
        result.add("typescript", endpoint(tsPacket.symbol(), tsPacket.className(), tsPacket.path()));
        return result;
    }

    private static JsonObject endpoint(String symbol, String className, String path) {
        JsonObject result = new JsonObject();
        result.addProperty("symbol", symbol);
        result.addProperty("className", className);
        result.addProperty("path", path);
        return result;
    }

    private static JsonObject unpaired(
            String direction, String side, int header, String symbol, String path, String reason) {
        JsonObject result = new JsonObject();
        result.addProperty("direction", direction);
        result.addProperty("side", side);
        result.addProperty("header", header);
        result.addProperty("symbol", symbol);
        result.addProperty("path", path);
        result.addProperty("reason", reason);
        return result;
    }

    private String signature(WireSchema schema) {
        if (schema instanceof ScalarSchema scalar) return scalar.type();
        if (schema instanceof ListSchema list) return "list<" + list.countType() + ":"
                + list.item().stream().map(this::signature).toList() + ">";
        if (schema instanceof OptionalSchema optional) return "optional<" + optional.controller() + ":"
                + optional.fields().stream().map(this::signature).toList() + ">";
        VariantSchema variant = (VariantSchema) schema;
        return "variant<" + variant.discriminator() + ":" + variant.branches() + ">";
    }

    private JsonObject schemaJson(WireSchema schema) {
        JsonObject result = new JsonObject();
        if (schema instanceof ScalarSchema scalar) {
            result.addProperty("kind", "scalar");
            result.addProperty("type", scalar.type());
            result.addProperty("name", scalar.name());
        } else if (schema instanceof ListSchema list) {
            result.addProperty("kind", "list");
            result.addProperty("countType", list.countType());
            JsonArray items = new JsonArray();
            list.item().forEach(item -> items.add(schemaJson(item)));
            result.add("item", items);
        } else if (schema instanceof OptionalSchema optional) {
            result.addProperty("kind", "optional");
            result.addProperty("controller", optional.controller());
            JsonArray fields = new JsonArray();
            optional.fields().forEach(field -> fields.add(schemaJson(field)));
            result.add("fields", fields);
        } else {
            VariantSchema variant = (VariantSchema) schema;
            result.addProperty("kind", "variant");
            result.addProperty("discriminator", variant.discriminator());
            JsonObject branches = new JsonObject();
            variant.branches().forEach((key, branch) -> {
                JsonArray fields = new JsonArray();
                branch.forEach(field -> fields.add(schemaJson(field)));
                branches.add(key, fields);
            });
            result.add("branches", branches);
        }
        return result;
    }

    private JsonObject schemaJson(JsonObject schema) {
        JsonObject result = new JsonObject();
        String type = schema.get("type").getAsString();
        if (!type.equals("list") && !type.equals("optional") && !type.equals("variant")) {
            result.addProperty("kind", "scalar");
            result.addProperty("type", type);
            result.addProperty("name", schema.has("name") ? schema.get("name").getAsString() : "");
            return result;
        }
        result.addProperty("kind", type);
        if (type.equals("list")) {
            result.addProperty("countType", schema.get("countType").getAsString());
            JsonArray items = new JsonArray();
            schema.getAsJsonArray("item").forEach(item -> items.add(schemaJson(item.getAsJsonObject())));
            result.add("item", items);
        } else if (type.equals("optional")) {
            result.addProperty("controller", schema.get("controller").getAsString());
            JsonArray fields = new JsonArray();
            schema.getAsJsonArray("fields").forEach(field -> fields.add(schemaJson(field.getAsJsonObject())));
            result.add("fields", fields);
        } else {
            result.addProperty("discriminator", schema.get("discriminator").getAsString());
            JsonObject branches = new JsonObject();
            schema.getAsJsonObject("branches").entrySet().forEach(branch -> {
                JsonArray fields = new JsonArray();
                branch.getValue().getAsJsonArray().forEach(field -> fields.add(schemaJson(field.getAsJsonObject())));
                branches.add(branch.getKey(), fields);
            });
            result.add("branches", branches);
        }
        return result;
    }

    private String signature(JsonObject schema) {
        String type = schema.get("type").getAsString();
        if (!type.equals("list") && !type.equals("optional") && !type.equals("variant")) return type;
        return schema.toString().replaceAll("\\\"name\\\":\\\"[^\\\"]*\\\",?", "");
    }

    private static String key(String direction, int header) {
        return direction + ':' + header;
    }

    private String relative(Path source) {
        return repositoryRoot.relativize(source.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }
}
