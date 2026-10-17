package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.LegacyChestMigration.FurniRow;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.LegacyChestMigration.LegacyChest;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.LegacyChestMigration.Plan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The move from the old chest storage (everything as JSON in items.wired_data) to the AIR model.
 * Whatever a chest held must end up somewhere a player can reach it: nothing is dropped.
 */
class LegacyChestMigrationTest {
    private static final int OWNER = 7;
    private static final int DIAMONDS = 5;
    private static final int DUCKETS = 0;

    /** Records what the migration writes. */
    private static final class RecordingSink implements LegacyChestMigration.Sink {
        final Map<Integer, Integer> chestCoins = new HashMap<>();
        final Map<Integer, Integer> chestLevels = new HashMap<>();
        final Map<Integer, String> chestSettings = new HashMap<>();
        final List<FurniRow> stored = new ArrayList<>();
        final List<Long> sequences = new ArrayList<>();
        final List<FurniRow> givenItems = new ArrayList<>();
        final Map<Integer, Integer> credits = new HashMap<>();
        final Map<Integer, Integer> points = new HashMap<>();
        final List<Integer> cleared = new ArrayList<>();

        @Override
        public void createChest(int chestId, int kind, int coins, int capacityLevel, String settings) {
            this.chestCoins.put(chestId, coins);
            this.chestLevels.put(chestId, capacityLevel);
            this.chestSettings.put(chestId, settings);
        }

        @Override
        public void storeItem(int chestId, int ownerId, FurniRow furni, long sequence) {
            this.stored.add(furni);
            this.sequences.add(sequence);
        }

        @Override
        public void giveItem(int ownerId, FurniRow furni) {
            this.givenItems.add(furni);
        }

        @Override
        public void giveCredits(int ownerId, int amount) {
            this.credits.merge(ownerId, amount, Integer::sum);
        }

        @Override
        public void givePoints(int ownerId, int type, int amount) {
            this.points.merge(type, amount, Integer::sum);
        }

        @Override
        public void clearLegacyData(int chestId) {
            this.cleared.add(chestId);
        }
    }

    private static ChestFurniStoredItem furni(int baseItemId, String extradata, int stack, int sells) {
        ChestFurniStoredItem item = new ChestFurniStoredItem();
        item.baseItemId = baseItemId;
        item.extradata = extradata;
        item.limitedStack = stack;
        item.limitedSells = sells;
        return item;
    }

    private static LegacyChest coinChest(ChestStorage old) {
        return new LegacyChest(10, OWNER, "wf_storage_coins1", old.toJson());
    }

    private static LegacyChest furniChest(ChestStorage old) {
        return new LegacyChest(20, OWNER, "wf_storage_furni1", old.toJson());
    }

    @Test
    void aCoinChestKeepsItsCreditsAsCoinsAndHandsOtherCurrenciesBack() {
        ChestStorage old = new ChestStorage();
        old.add(ChestStorage.KIND_CURRENCY, -1, 1200);
        old.add(ChestStorage.KIND_CURRENCY, DIAMONDS, 30);
        old.add(ChestStorage.KIND_CURRENCY, DUCKETS, 45);
        old.setName("Bank");

        Plan plan = LegacyChestMigration.plan(coinChest(old));

        assertEquals(ChestStorage.KIND_CURRENCY, plan.kind());
        assertEquals(1200, plan.coins());
        assertEquals(Map.of(DIAMONDS, 30, DUCKETS, 45), plan.pointsToOwner());
        assertEquals(0, plan.creditsToOwner());
        assertTrue(plan.settings().contains("\"name\":\"Bank\""));

        RecordingSink sink = new RecordingSink();
        LegacyChestMigration.apply(plan, sink, 1);
        assertEquals(1200, sink.chestCoins.get(10));
        assertEquals(30, sink.points.get(DIAMONDS));
        assertEquals(45, sink.points.get(DUCKETS));
        assertEquals(List.of(10), sink.cleared);
    }

    @Test
    void aFurniChestKeepsEveryRowWithItsStateAndLimitedNumber() {
        ChestStorage old = new ChestStorage();
        old.addFurniItem(furni(301, "2", 0, 0));
        old.addFurniItem(furni(301, "0", 0, 0));
        old.addFurniItem(furni(999, "1", 50, 7));
        old.add(ChestStorage.KIND_CURRENCY, -1, 25);

        Plan plan = LegacyChestMigration.plan(furniChest(old));

        assertEquals(ChestStorage.KIND_FURNI, plan.kind());
        assertEquals(3, plan.storedFurni().size());
        assertEquals(new FurniRow(301, "2", "0:0"), plan.storedFurni().get(0));
        assertEquals(new FurniRow(999, "1", "50:7"), plan.storedFurni().get(2));
        assertEquals(25, plan.creditsToOwner(), "credits a furni chest cannot hold go back to the owner");

        RecordingSink sink = new RecordingSink();
        LegacyChestMigration.apply(plan, sink, 100);
        assertEquals(plan.storedFurni(), sink.stored);
        assertEquals(List.of(100L, 101L, 102L), sink.sequences, "the old order stays oldest first");
        assertEquals(25, sink.credits.get(OWNER));
    }

    @Test
    void theOldAggregatedFurniFormatIsExpandedIntoOneItemPerUnit() {
        String json = "{\"entries\":[{\"kind\":1,\"type\":42,\"quantity\":3}]}";

        Plan plan = LegacyChestMigration.plan(new LegacyChest(30, OWNER, "wf_storage_furni2", json));

        assertEquals(3, plan.storedFurni().size());
        assertTrue(plan.storedFurni().stream().allMatch(row -> row.baseItemId() == 42));
    }

    @Test
    void furniInACoinChestGoesToTheOwnersInventory() {
        ChestStorage old = new ChestStorage();
        old.addFurniItem(furni(55, "0", 0, 0));

        Plan plan = LegacyChestMigration.plan(coinChest(old));
        RecordingSink sink = new RecordingSink();
        LegacyChestMigration.apply(plan, sink, 1);

        assertTrue(sink.stored.isEmpty());
        assertEquals(List.of(new FurniRow(55, "0", "0:0")), sink.givenItems);
    }

    @Test
    void theCapacityLevelCoversWhatWasBoughtAndWhatIsHeld() {
        // An old furni chest at the old default (5000) gets the AIR level that holds 5000.
        Plan defaults = LegacyChestMigration.plan(furniChest(new ChestStorage()));
        assertEquals(4, defaults.capacityLevel());

        // An old coin chest at 5000 is the AIR starting coin chest.
        assertEquals(0, LegacyChestMigration.plan(coinChest(new ChestStorage())).capacityLevel());

        // Bought beyond the AIR top level: the top level, and every coin is kept.
        ChestStorage rich = new ChestStorage();
        rich.growCapacity(500_000);
        rich.add(ChestStorage.KIND_CURRENCY, -1, 300_000);
        Plan richPlan = LegacyChestMigration.plan(coinChest(rich));
        assertEquals(ChestCapacity.COINS_MAX_LEVEL, richPlan.capacityLevel());
        assertEquals(300_000, richPlan.coins());
    }

    @Test
    void aCeilingTheOwnerLoweredStaysLowered() {
        ChestStorage old = new ChestStorage();
        old.setCapacity(300);

        Plan plan = LegacyChestMigration.plan(furniChest(old));
        ChestStorage migrated = ChestStorage.fromStore(
                plan.settings(), 0, plan.capacityLevel(), List.of(), ChestStorage.KIND_FURNI, false);

        assertEquals(300, migrated.getCapacity());
        assertEquals(5000, migrated.getCapacityMax());
    }

    @Test
    void lockUpgradeAndNotificationSettingsCarryOver() {
        ChestStorage old = new ChestStorage();
        old.enableWired();
        old.setAutoLock(true);
        old.setNotifications(true, false, true, false, true, 1);

        Plan plan = LegacyChestMigration.plan(coinChest(old));
        ChestStorage migrated =
                ChestStorage.fromStore(plan.settings(), plan.coins(), plan.capacityLevel(), List.of(), 0, false);

        assertTrue(migrated.isWiredEnabled());
        assertTrue(migrated.isLocked());
        assertTrue(migrated.isAutoLock());
        assertTrue(migrated.isNotifyFull());
        assertTrue(migrated.isNotifyWired());
        assertEquals(1, migrated.getNotifyMode());
    }

    @Test
    void aChestSavedBeforeTheUpgradeExistedStaysWired() {
        Plan plan = LegacyChestMigration.plan(new LegacyChest(40, OWNER, "wf_storage_coins2", "{\"entries\":[]}"));
        ChestStorage migrated = ChestStorage.fromStore(plan.settings(), 0, plan.capacityLevel(), List.of(), 0, false);

        assertTrue(migrated.isWiredEnabled());
    }

    @Test
    void anEmptyOrBrokenPayloadBecomesAnEmptyChest() {
        for (String json : new String[] {"", null, "not json", "{\"entries\":\"x\"}"}) {
            Plan plan = LegacyChestMigration.plan(new LegacyChest(50, OWNER, "wf_storage_furni1", json));
            assertEquals(0, plan.coins());
            assertTrue(plan.storedFurni().isEmpty());
        }
    }

    @Test
    void withoutAnOwnerTheOldDataIsKeptWhenSomethingWouldBeHandedBack() {
        ChestStorage old = new ChestStorage();
        old.add(ChestStorage.KIND_CURRENCY, DIAMONDS, 10);

        RecordingSink sink = new RecordingSink();
        LegacyChestMigration.apply(
                LegacyChestMigration.plan(new LegacyChest(60, 0, "wf_storage_coins1", old.toJson())), sink, 1);

        assertFalse(sink.cleared.contains(60), "nothing is cleared that could not be handed to anyone");
        assertTrue(sink.points.isEmpty());
    }

    @Test
    void theStarterChestKeepsItsContentsAboveItsNewCeiling() {
        ChestStorage old = new ChestStorage();
        for (int i = 0; i < 150; i++) old.addFurniItem(furni(9, "0", 0, 0));

        Plan plan = LegacyChestMigration.plan(new LegacyChest(70, OWNER, "wf_storage_furni_starter", old.toJson()));

        assertEquals(150, plan.storedFurni().size());
        assertEquals(0, plan.capacityLevel());
    }
}
