package polaris.hotel.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogAssetTransactionContractTest {
    private static String itemManagerSource() throws Exception {
        return Files.readString(Path.of("src/main/java/polaris/hotel/items/ItemManager.java"))
                .replaceAll("\\s+", "");
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        int end = source.indexOf("public", start + signature.length());
        if (end < 0) end = source.length();
        return source.substring(start, end);
    }

    @Test
    void furnitureFactoriesCanShareTheCatalogTransactionConnection() throws Exception {
        String source = itemManagerSource();

        assertTrue(source.contains("createItem(Connectionconnection,intuserId"));
        assertTrue(source.contains("insertTeleportPair(Connectionconnection,intitemOneId,intitemTwoId)"));
        assertTrue(source.contains("insertHopper(Connectionconnection,UserItemhopper)"));
    }

    @Test
    void connectionAwareFactoriesDoNotOpenAnotherConnection() throws Exception {
        String source = itemManagerSource();
        assertTrue(!method(source, "createItem(Connectionconnection,intuserId")
                .contains("getDataSource().getConnection()"));
        assertTrue(
                !method(source, "insertTeleportPair(Connectionconnection").contains("getDataSource().getConnection()"));
        assertTrue(!method(source, "insertHopper(Connectionconnection").contains("getDataSource().getConnection()"));
    }
}
