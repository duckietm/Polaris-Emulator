package com.eu.habbo.tools.furni;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FurniImportPipelineContractTest {
    @Test
    void canonicalProcedureCoversEveryDatabaseAndAssetLayer() throws Exception {
        String documentation = Files.readString(Path.of("../docs/FURNI_IMPORT_PIPELINE.md"));
        String wrapper = Files.readString(Path.of("../scripts/verify-furni-import.ps1"));

        for (String required : new String[]{
                "items_base", "catalog_items", "FurnitureData.json", ".nitro", "_icon.png",
                "logic.credits", "MariaDB transaction", "JSON report"}) {
            assertTrue(documentation.contains(required), required);
        }
        assertTrue(wrapper.contains("FurniConsistencyCli"));
        assertTrue(wrapper.contains("--items-sql-dump"));
        assertTrue(wrapper.contains("--furniture-data"));
        assertTrue(wrapper.contains("--bundles"));
        assertTrue(wrapper.contains("--icons"));
    }
}
