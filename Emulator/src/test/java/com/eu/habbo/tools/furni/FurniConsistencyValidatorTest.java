package com.eu.habbo.tools.furni;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurniConsistencyValidatorTest {
    @TempDir
    Path temp;

    @Test
    void validatesDatabaseFurnitureDataBundleIconAndCreditLogicTogether() throws Exception {
        Path bundles = Files.createDirectories(temp.resolve("bundles"));
        Path icons = Files.createDirectories(temp.resolve("icons"));
        writeBundle(bundles.resolve("CF_50_goldbar.nitro"), "CF_50_goldbar", "50");
        Files.write(icons.resolve("CF_50_goldbar_icon.png"), new byte[]{1});

        var item = new FurniConsistencyValidator.ItemBase(2066, 2066, "CF_50_goldbar", "s", "default");
        JsonObject furnitureData = furnitureData("CF_50_goldbar", 2066, false);
        var report = FurniConsistencyValidator.validate(
                List.of(item), furnitureData, bundles, icons, null);

        assertTrue(report.findings().isEmpty());
        assertEquals(1, report.summary().itemsChecked());
        assertEquals(1, report.summary().bundlesChecked());
    }

    @Test
    void reportsDuplicatesMappingAssetsAndCreditLogicWithStableCodes() throws Exception {
        Path bundles = Files.createDirectories(temp.resolve("broken-bundles"));
        Path icons = Files.createDirectories(temp.resolve("broken-icons"));
        writeBundle(bundles.resolve("CF_50_goldbar.nitro"), "CF_50_goldbar", "5");

        var items = List.of(
                new FurniConsistencyValidator.ItemBase(1, 2066, "CF_50_goldbar", "s", "default"),
                new FurniConsistencyValidator.ItemBase(2, 9999, "missing_asset", "i", "default"));
        JsonObject furnitureData = JsonParser.parseString("""
                {"roomitemtypes":{"furnitype":[
                  {"id":2066,"classname":"CF_50_goldbar"},
                  {"id":999,"classname":"CF_50_goldbar"}
                ]},"wallitemtypes":{"furnitype":[]}}
                """).getAsJsonObject();

        var report = FurniConsistencyValidator.validate(items, furnitureData, bundles, icons, null);
        var codes = report.findings().stream().map(FurniConsistencyValidator.Finding::code).toList();

        assertTrue(codes.contains("duplicate_classname"));
        assertTrue(codes.contains("missing_furniture_data"));
        assertTrue(codes.contains("missing_bundle"));
        assertTrue(codes.contains("missing_icon"));
        assertTrue(codes.contains("credit_logic_mismatch"));
    }

    @Test
    void legacyVariantSuffixUsesTheSharedPortableAssetName() throws Exception {
        Path bundles = Files.createDirectories(temp.resolve("path-bundles"));
        Path icons = Files.createDirectories(temp.resolve("path-icons"));
        writeBundle(bundles.resolve("table_plasto_4leg.nitro"), "table_plasto_4leg", "0");
        Files.write(icons.resolve("table_plasto_4leg_icon.png"), new byte[]{1});
        var item = new FurniConsistencyValidator.ItemBase(9, 9, "table_plasto_4leg*1", "s", "default");

        var report = FurniConsistencyValidator.validate(
                List.of(item), furnitureData("table_plasto_4leg*1", 9, false), bundles, icons, null);

        assertTrue(report.findings().stream()
                .noneMatch(finding -> finding.code().equals("invalid_asset_path")));
        assertTrue(report.findings().stream()
                .noneMatch(finding -> finding.code().equals("missing_bundle")));
    }

    @Test
    void acceptsZlibCompressedNitroJsonEntries() throws Exception {
        Path bundles = Files.createDirectories(temp.resolve("zlib-bundles"));
        Path icons = Files.createDirectories(temp.resolve("zlib-icons"));
        writeBundle(bundles.resolve("chair_basic.nitro"), "chair_basic", "0", false);
        Files.write(icons.resolve("chair_basic_icon.png"), new byte[]{1});

        var report = FurniConsistencyValidator.validate(
                List.of(new FurniConsistencyValidator.ItemBase(11, 11, "chair_basic", "s", "default")),
                furnitureData("chair_basic", 11, false), bundles, icons, null);

        assertTrue(report.findings().stream().noneMatch(finding -> finding.code().equals("invalid_bundle")));
    }

    @Test
    void skipsNonFurnitureAndVirtualPetRows() throws Exception {
        Path bundles = Files.createDirectories(temp.resolve("virtual-bundles"));
        Path icons = Files.createDirectories(temp.resolve("virtual-icons"));
        var items = List.of(
                new FurniConsistencyValidator.ItemBase(1, 0, "bot_generic", "r", "default"),
                new FurniConsistencyValidator.ItemBase(2, 50000, "a0 pet0", "s", "default"));

        var report = FurniConsistencyValidator.validate(items,
                JsonParser.parseString("{\"roomitemtypes\":{\"furnitype\":[]},\"wallitemtypes\":{\"furnitype\":[]}}")
                        .getAsJsonObject(), bundles, icons, null);

        assertEquals(0, report.summary().itemsChecked());
        assertTrue(report.findings().isEmpty());
    }

    private static JsonObject furnitureData(String classname, int id, boolean wall) {
        String room = wall ? "[]" : "[{\"id\":" + id + ",\"classname\":\"" + classname + "\"}]";
        String walls = wall ? "[{\"id\":" + id + ",\"classname\":\"" + classname + "\"}]" : "[]";
        return JsonParser.parseString("{\"roomitemtypes\":{\"furnitype\":" + room
                + "},\"wallitemtypes\":{\"furnitype\":" + walls + "}}").getAsJsonObject();
    }

    private static void writeBundle(Path target, String classname, String credits) throws Exception {
        writeBundle(target, classname, credits, true);
    }

    private static void writeBundle(Path target, String classname, String credits, boolean gzipCompression)
            throws Exception {
        byte[] json = ("{\"type\":\"furniture\",\"name\":\"" + classname
                + "\",\"logicType\":\"furniture_credit\",\"logic\":{\"credits\":\""
                + credits + "\"}}").getBytes(StandardCharsets.UTF_8);
        byte[] compressed;
        try (var bytes = new java.io.ByteArrayOutputStream();
             var compressedOutput = gzipCompression ? new GZIPOutputStream(bytes) : new DeflaterOutputStream(bytes)) {
            compressedOutput.write(json);
            compressedOutput.finish();
            compressed = bytes.toByteArray();
        }
        byte[] name = (classname + ".json").getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = Files.newOutputStream(target); DataOutputStream data = new DataOutputStream(output)) {
            data.writeShort(1);
            data.writeShort(name.length);
            data.write(name);
            data.writeInt(compressed.length);
            data.write(compressed);
        }
    }
}
