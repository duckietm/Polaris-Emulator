package polaris.hotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AtomicMarketPlacePurchaseContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void purchaseTransactionOwnsOfferItemAndBuyerBalance() throws Exception {
        String source = read("src/main/java/polaris/hotel/catalog/marketplace/MarketPlacePurchaseTransaction.java");
        String compactSource = source.replaceAll("\\s+", "");

        assertTrue(source.contains("setAutoCommit(false)"));
        assertTrue(source.contains(
                "UPDATE marketplace_items SET state = 2, sold_timestamp = ? WHERE id = ? AND state = 1"));
        assertTrue(source.contains("UPDATE items SET user_id = ? WHERE id = ?"));
        assertTrue(compactSource.contains("EconomyLedger.apply(connection"));
        assertTrue(source.contains("\"marketplace:offer:\" + offerId + \":buyer\""));
        assertTrue(source.contains("\"catalog.marketplace.buy\""));
        assertTrue(source.contains("connection.commit()"));
        assertTrue(source.contains("connection.rollback()"));
    }

    @Test
    void inMemoryTransferOnlyHappensAfterDatabaseCommit() throws Exception {
        String source = read("src/main/java/polaris/hotel/catalog/marketplace/MarketPlace.java");
        int commit = source.indexOf("MarketPlacePurchaseTransaction.commit(");
        int setOwner = source.indexOf("item.setUserId(", commit);
        int inventoryAdd = source.indexOf("addItem(item)", commit);

        assertTrue(commit > -1, "marketplace purchase must use the transaction");
        assertTrue(commit < setOwner, "item memory owner must change after commit");
        assertTrue(commit < inventoryAdd, "buyer inventory must change after commit");
    }
}
