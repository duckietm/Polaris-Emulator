package com.eu.habbo.habbohotel.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogAssetTransactionContractTest {
    private static String itemManagerSource() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/items/ItemManager.java"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        int end = source.indexOf("\n    public ", start + signature.length());
        return source.substring(start, end);
    }

    @Test
    void furnitureFactoriesCanShareTheCatalogTransactionConnection() throws Exception {
        String source = itemManagerSource();

        assertTrue(source.contains("createItem(Connection connection, int habboId"));
        assertTrue(source.contains("insertTeleportPair(Connection connection, int itemOneId, int itemTwoId)"));
        assertTrue(source.contains("insertHopper(Connection connection, HabboItem hopper)"));
    }

    @Test
    void connectionAwareFactoriesDoNotOpenAnotherConnection() throws Exception {
        String source = itemManagerSource();
        assertTrue(!method(source, "createItem(Connection connection, int habboId")
                .contains("getDataSource().getConnection()"));
        assertTrue(!method(source, "insertTeleportPair(Connection connection")
                .contains("getDataSource().getConnection()"));
        assertTrue(!method(source, "insertHopper(Connection connection")
                .contains("getDataSource().getConnection()"));
    }
}
