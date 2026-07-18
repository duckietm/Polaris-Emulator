package com.eu.habbo.tools.furni;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public final class FurniConsistencyValidator {
    private static final Pattern SIMPLE_REDEEM = Pattern.compile("^(?:CF|CFC|PF)_(\\d+)(?:_|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIAMOND_REDEEM = Pattern.compile("^CF_DIAMOND_(\\d+)(?:_|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TYPED_REDEEM = Pattern.compile("^DF_\\d+_(\\d+)(?:_|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INVALID_ASSET_PATH = Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1f]");
    private static final Pattern RESERVED_WINDOWS_NAME = Pattern.compile(
            "^(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$", Pattern.CASE_INSENSITIVE);

    private FurniConsistencyValidator() {
    }

    public static Report validate(List<ItemBase> items, JsonObject furnitureData, Path bundlesDirectory,
                                  Path iconsDirectory, Path swfDirectory) {
        List<Finding> findings = new ArrayList<>();
        Map<String, List<FurnitureEntry>> byClassname = indexFurnitureData(furnitureData, findings);
        int bundlesChecked = 0;
        int itemsChecked = 0;

        for (ItemBase item : items) {
            if (!requiresFurnitureAssets(item)) continue;
            itemsChecked++;
            String key = normalize(item.itemName());
            List<FurnitureEntry> entries = byClassname.getOrDefault(key, List.of());
            if (entries.isEmpty()) {
                findings.add(finding("missing_furniture_data", item,
                        "classname is absent from FurnitureData.json"));
            } else {
                boolean expectsWall = "i".equalsIgnoreCase(item.type());
                boolean mapped = entries.stream().anyMatch(entry -> entry.id() == item.spriteId()
                        && entry.wall() == expectsWall);
                if (!mapped) {
                    findings.add(finding("id_classname_mismatch", item,
                            "sprite_id/type does not match the FurnitureData classname entry"));
                }
            }

            String assetName = assetName(item.itemName());
            if (!isPortableAssetName(assetName)) {
                findings.add(finding("invalid_asset_path", item,
                        "classname cannot be represented as a portable Windows/Linux asset filename"));
                continue;
            }

            Path bundle = bundlesDirectory.resolve(assetName + ".nitro");
            if (!Files.isRegularFile(bundle)) {
                findings.add(finding("missing_bundle", item, "missing .nitro furniture bundle"));
            } else {
                bundlesChecked++;
                validateBundle(item, assetName, bundle, findings);
            }

            Path icon = iconsDirectory.resolve(assetName + "_icon.png");
            if (!Files.isRegularFile(icon)) {
                findings.add(finding("missing_icon", item, "missing legacy/catalog icon"));
            }

            if (swfDirectory != null && !Files.isRegularFile(swfDirectory.resolve(assetName + ".swf"))) {
                findings.add(finding("missing_swf", item, "missing optional legacy SWF asset"));
            }
        }

        return new Report(List.copyOf(findings),
                new Summary(itemsChecked, byClassname.values().stream().mapToInt(List::size).sum(), bundlesChecked));
    }

    private static Map<String, List<FurnitureEntry>> indexFurnitureData(JsonObject root, List<Finding> findings) {
        Map<String, List<FurnitureEntry>> indexed = new HashMap<>();
        addFurnitureSection(root, "roomitemtypes", false, indexed);
        addFurnitureSection(root, "wallitemtypes", true, indexed);
        indexed.forEach((classname, entries) -> {
            if (entries.size() > 1) {
                findings.add(new Finding("duplicate_classname", null, classname,
                        "FurnitureData contains " + entries.size() + " entries for the same classname"));
            }
        });
        return indexed;
    }

    private static void addFurnitureSection(JsonObject root, String section, boolean wall,
                                            Map<String, List<FurnitureEntry>> target) {
        if (!root.has(section) || !root.get(section).isJsonObject()) return;
        JsonObject wrapper = root.getAsJsonObject(section);
        if (!wrapper.has("furnitype") || !wrapper.get("furnitype").isJsonArray()) return;
        for (JsonElement element : wrapper.getAsJsonArray("furnitype")) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("id") || !entry.has("classname")) continue;
            String classname = entry.get("classname").getAsString();
            target.computeIfAbsent(normalize(classname), ignored -> new ArrayList<>())
                    .add(new FurnitureEntry(entry.get("id").getAsInt(), classname, wall));
        }
    }

    private static void validateBundle(ItemBase item, String assetName, Path bundle, List<Finding> findings) {
        try {
            JsonObject json = readFurnitureJson(bundle, assetName);
            if (!json.has("name") || !assetName.equalsIgnoreCase(json.get("name").getAsString())) {
                findings.add(finding("bundle_classname_mismatch", item,
                        "bundle metadata name does not match items_base.item_name"));
            }

            OptionalInt expectedCredits = expectedCredits(item.itemName());
            if (expectedCredits.isPresent()) {
                String logicType = json.has("logicType") ? json.get("logicType").getAsString() : "";
                JsonObject logic = json.has("logic") && json.get("logic").isJsonObject()
                        ? json.getAsJsonObject("logic") : new JsonObject();
                int actualCredits = logic.has("credits") ? logic.get("credits").getAsInt() : -1;
                if (!"furniture_credit".equals(logicType) || actualCredits != expectedCredits.getAsInt()) {
                    findings.add(finding("credit_logic_mismatch", item,
                            "bundle logic.credits must equal redeemable classname value "
                                    + expectedCredits.getAsInt()));
                }
            }
        } catch (Exception exception) {
            findings.add(finding("invalid_bundle", item, exception.getMessage()));
        }
    }

    static JsonObject readFurnitureJson(Path bundle, String classname) throws IOException {
        try (DataInputStream input = new DataInputStream(Files.newInputStream(bundle))) {
            int count = input.readUnsignedShort();
            for (int index = 0; index < count; index++) {
                int nameLength = input.readUnsignedShort();
                String name = new String(input.readNBytes(nameLength), StandardCharsets.UTF_8);
                int compressedLength = input.readInt();
                if (compressedLength < 0 || compressedLength > 64 * 1024 * 1024) {
                    throw new IOException("invalid compressed entry length");
                }
                byte[] compressed = input.readNBytes(compressedLength);
                if (compressed.length != compressedLength) throw new EOFException("truncated bundle entry");
                if (name.equalsIgnoreCase(classname + ".json")) {
                    try (var decompressed = compressed.length > 2
                            && (compressed[0] & 0xff) == 0x1f && (compressed[1] & 0xff) == 0x8b
                            ? new GZIPInputStream(new ByteArrayInputStream(compressed))
                            : new InflaterInputStream(new ByteArrayInputStream(compressed))) {
                        return JsonParser.parseString(new String(decompressed.readAllBytes(), StandardCharsets.UTF_8))
                                .getAsJsonObject();
                    }
                }
            }
        }
        throw new IOException("bundle is missing " + classname + ".json");
    }

    private static OptionalInt expectedCredits(String classname) {
        for (Pattern pattern : List.of(DIAMOND_REDEEM, TYPED_REDEEM, SIMPLE_REDEEM)) {
            Matcher matcher = pattern.matcher(classname);
            if (matcher.find()) {
                try {
                    int value = Integer.parseInt(matcher.group(1));
                    return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
                } catch (NumberFormatException ignored) {
                    return OptionalInt.empty();
                }
            }
        }
        return OptionalInt.empty();
    }

    private static Finding finding(String code, ItemBase item, String message) {
        return new Finding(code, item.id(), item.itemName(), message);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static boolean isPortableAssetName(String value) {
        return value != null && !value.isBlank()
                && !value.endsWith(".") && !value.endsWith(" ")
                && !INVALID_ASSET_PATH.matcher(value).find()
                && !RESERVED_WINDOWS_NAME.matcher(value).matches();
    }

    private static String assetName(String classname) {
        if (classname == null) return "";
        int variant = classname.indexOf('*');
        return variant > 0 ? classname.substring(0, variant) : classname;
    }

    private static boolean requiresFurnitureAssets(ItemBase item) {
        if (!("s".equalsIgnoreCase(item.type()) || "i".equalsIgnoreCase(item.type()))) return false;
        String name = normalize(item.itemName());
        return !name.startsWith("a0 pet");
    }

    public record ItemBase(
            int id,
            @SerializedName("sprite_id") int spriteId,
            @SerializedName("item_name") String itemName,
            String type,
            @SerializedName("interaction_type") String interactionType) {
    }

    private record FurnitureEntry(int id, String classname, boolean wall) {
    }

    public record Finding(String code, Integer itemId, String classname, String message) {
    }

    public record Summary(int itemsChecked, int furnitureDataEntries, int bundlesChecked) {
    }

    public record Report(List<Finding> findings, Summary summary) {
        public boolean valid() {
            return findings.isEmpty();
        }
    }
}
