package polaris.hotel.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CatalogPurchaseAtomicityContractTest {
    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/java", relativePath));
    }

    private static String withoutWhitespace(String source) {
        return source.replaceAll("\\s+", "");
    }

    @Test
    void normalPurchasePersistsAssetsAndPaymentInOneTransaction() throws Exception {
        String manager = source("polaris/hotel/catalog/CatalogManager.java");
        String transaction = source("polaris/hotel/catalog/CatalogPurchaseTransaction.java");

        assertTrue(
                manager.contains("synchronized (user.getUserStats())"),
                "normal catalog purchases must acquire the per-user purchase gate atomically");
        assertTrue(
                manager.contains("CatalogPurchaseTransaction.execute("),
                "normal catalog assets and payment must use the transaction coordinator");
        assertTrue(transaction.contains("connection.setAutoCommit(false)"));
        assertTrue(
                withoutWhitespace(transaction).contains("EconomyLedger.apply(connection"),
                "credit debit must use the locked, audited economy ledger");
        String ledger = source("polaris/hotel/economy/EconomyLedger.java");
        assertTrue(
                ledger.contains("int balanceAfter = checkedBalance(balanceBefore, operation.delta())"),
                "the ledger must reject insufficient concurrent balances");
        assertTrue(transaction.contains("connection.commit()"));
        assertTrue(transaction.contains("connection.rollback()"));
        assertTrue(
                manager.contains("limitedConfiguration.restoreNumber(item.getId(), limitedNumber)"),
                "a failed legacy limited purchase must return its reserved number to the pool");
    }

    @Test
    void giftsChargeCreateAndSellInOneTransaction() throws Exception {
        String gift = withoutWhitespace(source("polaris/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java"));

        int debit = gift.indexOf("CatalogPaymentService.takeWithin(connection,this.client.getUser(),chargeCredits,"
                + "item.getPointsType(),chargePoints)");
        int create = gift.indexOf("getItemManager().createGiftContent(connection,userId", debit);
        assertTrue(
                debit > -1 && create > debit,
                "gift payment and contents must be written on the gift's connection, payment first");
        assertTrue(
                gift.contains("getItemManager().createItem(connection,userId,giftItem,0,0,giftData)"),
                "the gift box must be written in the same transaction");
        assertTrue(
                gift.contains("this.recordLimitedSale(connection,item,attempt);connection.commit();"),
                "the limited sale must be the last write before the gift is committed");
        assertTrue(
                gift.contains("if(!committed){try{connection.rollback();"),
                "a gift that is not committed must be rolled back");
        assertTrue(
                gift.contains("privatevoidundo(GiftAttemptattempt){if(attempt.committed)return;")
                        && !gift.contains("attempt.paidCredits")
                        && !gift.contains("tryDeleteItem("),
                "a rolled back gift has nothing to refund or delete");
    }

    @Test
    void clubPurchasesUseTheSamePurchaseGateAndWalletDebit() throws Exception {
        String handler = source("polaris/messages/incoming/catalog/CatalogBuyItemEvent.java");
        assertTrue(
                handler.contains("new CatalogPurchaseApplicationService("),
                "the incoming handler must delegate the parsed command to the purchase application service");
        String buy =
                withoutWhitespace(source("polaris/messages/incoming/catalog/CatalogPurchaseApplicationService.java"));

        assertTrue(buy.contains("isPurchasingFurniture=true"));
        assertTrue(buy.contains(
                "CatalogPaymentService.tryTake(this.client.getUser(),paidCredits,item.getPointsType(),paidPoints)"));
        assertTrue(
                buy.contains(
                        "CatalogPaymentService.refund(this.client.getUser(),paidCredits,item.getPointsType(),paidPoints)"),
                "a rejected subscription grant must restore its payment");
    }
}
