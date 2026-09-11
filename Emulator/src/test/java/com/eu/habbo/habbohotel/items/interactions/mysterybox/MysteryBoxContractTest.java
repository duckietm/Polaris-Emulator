package com.eu.habbo.habbohotel.items.interactions.mysterybox;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MysteryBoxContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    private static String mysteryBox(String name) throws Exception {
        return source("com/eu/habbo/habbohotel/items/interactions/mysterybox/" + name + ".java");
    }

    @Test
    void bothHalvesOfTheFurnitureAreRegistered() throws Exception {
        String items = source("com/eu/habbo/habbohotel/items/ItemManager.java");

        assertTrue(items.contains("new ItemInteraction(\"mystery_box\", InteractionMysteryBox.class)"));
        assertTrue(items.contains("new ItemInteraction(\"mystery_key\", InteractionMysteryBoxKey.class)"));
    }

    @Test
    void walkingAwayFromAWaitIsAHeaderTheRendererSends() throws Exception {
        String incoming = source("com/eu/habbo/messages/incoming/Incoming.java");
        String registry = source("com/eu/habbo/messages/PacketManager.java");

        assertTrue(incoming.contains("MysteryBoxWaitingCanceledEvent = 2012"));
        assertTrue(registry.contains("Incoming.MysteryBoxWaitingCanceledEvent, MysteryBoxWaitingCanceledEvent.class"));
    }

    @Test
    void aBoxOnlyOpensAWaitOnSomebodyElsesKey() throws Exception {
        String manager = mysteryBox("MysteryBoxManager");

        assertTrue(manager.contains("if (box.getUserId() != owner) return;"));
        assertTrue(manager.contains("if (item.getUserId() == boxOwnerId) continue;"));
        assertTrue(
                manager.contains("if (!MysteryBoxColour.of(item.getBaseItem().getName()).equals(colour)) continue;"));
    }

    @Test
    void theWaitIsOnlySentOnceBothSidesAreThere() throws Exception {
        String manager = mysteryBox("MysteryBoxManager");

        assertTrue(manager.contains("if (this.waitOn(box.getId()) != null) return;"));
        assertTrue(manager.contains("if (keyOwner == null || keyOwner.getClient() == null) return;"));
        assertTrue(manager.indexOf("this.waits.put(box.getId(), wait);")
                < manager.indexOf("MysteryBoxSupport.sendWait(habbo);"));
    }

    @Test
    void theKeyEndsTheWaitAndBothSidesAreRewardedAndBothPiecesUsedUp() throws Exception {
        String manager = mysteryBox("MysteryBoxManager");

        assertTrue(manager.contains("MysteryBoxSupport.sendPrize(boxOwner, prizes.award(boxOwner, colour));"));
        assertTrue(manager.contains("MysteryBoxSupport.sendPrize(habbo, prizes.award(habbo, colour));"));
        assertTrue(manager.contains("this.consume(room, box);"));
        assertTrue(manager.contains("this.consume(room, key);"));
        assertTrue(
                manager.indexOf("this.waits.remove(wait.boxItemId());") < manager.indexOf("this.consume(room, box);"));
    }

    @Test
    void aWaitNobodyAnswersExpiresOnItsOwn() throws Exception {
        String manager = mysteryBox("MysteryBoxManager");

        assertTrue(manager.contains("if (Emulator.getIntUnixTimestamp() - wait.openedAt() > WAIT_SECONDS)"));
        assertTrue(manager.contains("this.waits.remove(boxItemId, wait);"));
    }

    @Test
    void thePrizePoolIsCatalogItemsAndAColourlessRowBelongsToEveryBox() throws Exception {
        String prizes = mysteryBox("MysteryBoxPrizes");

        assertTrue(prizes.contains("WHERE enabled = 1"));
        assertTrue(prizes.contains("AND (colour = ? OR colour = '')"));
        assertTrue(prizes.contains("getCatalogItem(this.draw(pool).catalogItemId())"));
        assertTrue(prizes.contains("for (Item baseItem : catalogItem.getBaseItems())"));
    }

    @Test
    void theTableTheMigrationCreatesIsTheOneTheContractDeclares() throws Exception {
        String migration =
                Files.readString(Path.of("src/main/resources/db/migration/V20260911120000__mystery_box.sql"));
        String contract = Files.readString(Path.of("src/main/resources/db/runtime-schema-contract.json"));

        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS `mystery_box_prizes`"));
        assertTrue(contract.contains(
                "\"mystery_box_prizes\": [\"catalog_item_id\", \"colour\", \"enabled\", \"id\", \"weight\"]"));

        for (String column : new String[] {"catalog_item_id", "colour", "enabled", "id", "weight"}) {
            assertTrue(migration.contains("`" + column + "`"), "the migration is missing " + column);
        }
    }
}
