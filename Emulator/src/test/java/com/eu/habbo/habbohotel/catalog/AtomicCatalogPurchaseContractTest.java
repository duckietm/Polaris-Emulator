package com.eu.habbo.habbohotel.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicCatalogPurchaseContractTest {
    @Test
    void coordinatorCommitsAssetsAndConditionalChargesTogether() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/catalog/CatalogPurchaseTransaction.java"));

        int begin = source.indexOf("connection.setAutoCommit(false)");
        int persist = source.indexOf("work.persist(connection)", begin);
        int credit = source.indexOf("chargeCredits(connection", persist);
        int points = source.indexOf("chargePoints(connection", credit);
        int commit = source.indexOf("connection.commit()", points);

        assertTrue(begin > -1);
        assertTrue(persist > begin);
        assertTrue(credit > persist);
        assertTrue(points > credit);
        assertTrue(commit > points);
        assertTrue(source.contains("EconomyLedger.apply(connection"));
        assertTrue(source.contains("\"catalog.purchase\""));
        assertTrue(source.contains("operationId + \":credits\""));
        assertTrue(source.contains("operationId + \":points\""));
        assertTrue(source.contains("connection.rollback()"));
    }

    @Test
    void catalogPublishesFurnitureOnlyAfterAtomicPurchaseReturns() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java"));

        int method = source.indexOf("purchaseFurnitureAtomically(");
        int transaction = source.indexOf("CatalogPurchaseTransaction.execute(", method);
        int inventory = source.indexOf("getItemsComponent().addItems(", transaction);
        int success = source.indexOf("new PurchaseOKComposer(", transaction);

        assertTrue(method > -1);
        assertTrue(transaction > method);
        assertTrue(inventory > transaction);
        assertTrue(success > transaction);
    }

    @Test
    void limitedFurnitureReservationUsesThePurchaseConnection() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/catalog/CatalogLimitedConfiguration.java"));

        assertTrue(source.contains("limitedSold(Connection connection, int catalogItemId"));
        assertTrue(source.contains("AND number = ? AND user_id = 0 LIMIT 1"));
        assertTrue(source.contains("executeUpdate() != 1"));
    }

    @Test
    void badgesAndEffectsPublishOnlyAfterTheirTransactionCommits() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java"));

        int method = source.indexOf("purchaseEntitlementsAtomically(");
        int transaction = source.indexOf("CatalogPurchaseTransaction.execute(", method);
        int badgeInsert = source.indexOf("badge.insert(connection)", transaction);
        int effectInsert = source.indexOf("EffectsComponent.persistEffect(connection", transaction);
        int badgePublish = source.indexOf("getBadgesComponent().addBadge(", effectInsert);
        int effectPublish = source.indexOf("getEffectsComponent().publishEffect(", effectInsert);

        assertTrue(method > -1);
        assertTrue(transaction > method);
        assertTrue(badgeInsert > transaction);
        assertTrue(effectInsert > transaction);
        assertTrue(badgePublish > effectInsert);
        assertTrue(effectPublish > effectInsert);
    }

    @Test
    void botsAndPetsPublishOnlyAfterTheirTransactionCommits() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java"));

        int method = source.indexOf("purchaseBotsAndPetsAtomically(");
        int transaction = source.indexOf("CatalogPurchaseTransaction.execute(", method);
        int botPersist = source.indexOf("createBot(connection", transaction);
        int petPersist = source.indexOf("createPet(connection", transaction);
        int botPublish = source.indexOf("getBotsComponent().addBot(", petPersist);
        int petPublish = source.indexOf("getPetsComponent().addPet(", petPersist);

        assertTrue(method > -1);
        assertTrue(transaction > method);
        assertTrue(botPersist > transaction);
        assertTrue(petPersist > transaction);
        assertTrue(botPublish > petPersist);
        assertTrue(petPublish > petPersist);
    }

    @Test
    void guildAndMusicFurnitureUseTheFurnitureTransaction() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java"));
        int transaction = source.indexOf("CatalogPurchaseTransaction.execute(",
                source.indexOf("purchaseFurnitureAtomically("));
        int guildPersist = source.indexOf("persistGuild(connection", transaction);
        int musicCreate = source.indexOf("createMusicDiscExtraData(", transaction);
        int inventory = source.indexOf("getItemsComponent().addItems(", transaction);
        int musicAchievement = source.indexOf("MusicCollector", inventory);

        assertTrue(guildPersist > transaction);
        assertTrue(musicCreate > transaction);
        assertTrue(inventory > guildPersist);
        assertTrue(musicAchievement > inventory);
    }
}
