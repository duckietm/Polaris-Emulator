package com.eu.habbo.tools.furni;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FurniConsistencyReportArtifactTest {
    @TempDir
    Path temp;

    @Test
    void writesMachineReadableFixtureReportForCiArtifactUpload() throws Exception {
        var item = new FurniConsistencyValidator.ItemBase(7, 7, "missing_fixture", "s", "default");
        var furnitureData = JsonParser.parseString("""
                {"roomitemtypes":{"furnitype":[{"id":7,"classname":"missing_fixture"}]},
                 "wallitemtypes":{"furnitype":[]}}
                """).getAsJsonObject();
        var report = FurniConsistencyValidator.validate(
                List.of(item), furnitureData,
                Files.createDirectories(temp.resolve("bundles")),
                Files.createDirectories(temp.resolve("icons")), null);

        Path artifact = Path.of("target/furni-consistency-fixture.json");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, new GsonBuilder().setPrettyPrinting().create().toJson(report));

        assertTrue(Files.size(artifact) > 0);
        assertTrue(JsonParser.parseString(Files.readString(artifact)).getAsJsonObject().has("findings"));
    }
}
