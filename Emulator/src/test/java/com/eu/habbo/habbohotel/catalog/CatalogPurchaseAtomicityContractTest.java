package com.eu.habbo.habbohotel.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogPurchaseAtomicityContractTest {
    private static String source(String relativePath) throws Exception {
        return Files.readString(Path.of("src/main/java", relativePath));
    }

    @Test
    void normalPurchasePersistsAssetsAndPaymentInOneTransaction() throws Exception {
        String manager = source("com/eu/habbo/habbohotel/catalog/CatalogManager.java");
        String transaction = source("com/eu/habbo/habbohotel/catalog/CatalogPurchaseTransaction.java");

        assertTrue(manager.contains("synchronized (habbo.getHabboStats())"),
                "normal catalog purchases must acquire the per-user purchase gate atomically");
        assertTrue(manager.contains("CatalogPurchaseTransaction.execute("),
                "normal catalog assets and payment must use the transaction coordinator");
        assertTrue(transaction.contains("connection.setAutoCommit(false)"));
        assertTrue(transaction.contains(
                        "UPDATE users SET credits = credits - ? WHERE id = ? AND credits >= ?"),
                "credit debit must reject insufficient concurrent balances");
        assertTrue(transaction.contains("connection.commit()"));
        assertTrue(transaction.contains("connection.rollback()"));
        assertTrue(manager.contains("limitedConfiguration.restoreNumber(item.getId(), limitedNumber)"),
                "a failed legacy limited purchase must return its reserved number to the pool");
    }

    @Test
    void giftsDebitBeforeCreationAndCompensateOnFailure() throws Exception {
        String gift = source("com/eu/habbo/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java");

        int debit = gift.indexOf("CatalogPaymentService.tryTake(this.client.getHabbo(), chargeCredits, item.getPointsType(), chargePoints)");
        int create = gift.indexOf("getItemManager().createItem(userId", debit);
        assertTrue(debit > -1 && create > debit,
                "gift payment must be reserved before recipient-owned rows are created");
        assertTrue(gift.contains("if (!giftDelivered)"),
                "failed gift delivery must enter compensating cleanup");
        assertTrue(gift.contains("CatalogPaymentService.refund(this.client.getHabbo(), paidCredits, paidPointsType, paidPoints)"),
                "failed gift delivery must restore the exact reserved payment");
    }

    @Test
    void clubPurchasesUseTheSamePurchaseGateAndWalletDebit() throws Exception {
        String buy = source("com/eu/habbo/messages/incoming/catalog/CatalogBuyItemEvent.java");

        assertTrue(buy.contains("isPurchasingFurniture = true"));
        assertTrue(buy.contains("CatalogPaymentService.tryTake(this.client.getHabbo(), paidCredits, item.getPointsType(), paidPoints)"));
        assertTrue(buy.contains("CatalogPaymentService.refund(this.client.getHabbo(), paidCredits, item.getPointsType(), paidPoints)"),
                "a rejected subscription grant must restore its payment");
    }
}
