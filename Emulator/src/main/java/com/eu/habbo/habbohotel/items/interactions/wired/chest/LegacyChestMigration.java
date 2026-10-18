package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carries a chest from the old model (its whole state as JSON in {@code items.wired_data}) over to
 * the official AIR model ({@code wired_chests} plus real items in {@code wired_chest_items}).
 *
 * <p>Nothing a player owned is lost on the way:
 *
 * <ul>
 *   <li>a coin chest's credits become its coins;
 *   <li>every stored furni row becomes a real stored item, keeping its state and limited number;
 *   <li>what the AIR model cannot hold goes back to the chest's owner: duckets, diamonds and other
 *       points in a coin chest, any currency in a furni chest, and any furni in a coin chest;
 *   <li>the capacity level is chosen so the new ceiling covers both what the owner had bought and
 *       what the chest holds, up to the official top level. A chest above even that keeps all of its
 *       contents; it only stops taking more.
 * </ul>
 *
 * <p>The decisions are made here, in plain Java, so they can be tested without a database; the
 * Flyway migration only feeds rows in and writes the plan out through a {@link Sink}.
 */
public final class LegacyChestMigration {
    /** The interaction types of the chests, as {@code items_base.interaction_type} names them. */
    public static final List<String> COIN_CHEST_TYPES = List.of("wf_storage_coins1", "wf_storage_coins2");

    public static final List<String> FURNI_CHEST_TYPES =
            List.of("wf_storage_furni1", "wf_storage_furni2", "wf_storage_furni_starter");

    private LegacyChestMigration() {}

    /** One chest as the old model stored it. */
    public record LegacyChest(int itemId, int ownerId, String interactionType, String wiredData) {
        public boolean coinChest() {
            return COIN_CHEST_TYPES.contains(this.interactionType);
        }

        public boolean starter() {
            return this.interactionType != null && this.interactionType.endsWith("_starter");
        }
    }

    /** A furni that has to become a real item, in the chest or in the owner's inventory. */
    public record FurniRow(int baseItemId, String extraData, String limitedData) {}

    /** What the migration writes for one chest. */
    public record Plan(
            int chestId,
            int ownerId,
            int kind,
            int coins,
            int capacityLevel,
            String settings,
            List<FurniRow> storedFurni,
            List<FurniRow> furniToOwner,
            int creditsToOwner,
            Map<Integer, Integer> pointsToOwner) {}

    /** Where a plan is written. The migration's implementation is one database transaction per chest. */
    public interface Sink {
        void createChest(int chestId, int kind, int coins, int capacityLevel, String settings);

        /** Create a real item owned by nobody and park it in the chest. */
        void storeItem(int chestId, int ownerId, FurniRow furni, long sequence);

        /** Create a real item in the owner's inventory. */
        void giveItem(int ownerId, FurniRow furni);

        void giveCredits(int ownerId, int amount);

        void givePoints(int ownerId, int type, int amount);

        /** Clear the old JSON, so the chest is never read the old way again. */
        void clearLegacyData(int chestId);
    }

    /** Decide what becomes of one chest. Malformed JSON reads as an empty chest, as it always did. */
    public static Plan plan(LegacyChest chest) {
        ChestStorage old = ChestStorage.fromJson(chest.wiredData());
        int kind = chest.coinChest() ? ChestStorage.KIND_CURRENCY : ChestStorage.KIND_FURNI;
        boolean starter = chest.starter();

        int coins = 0;
        int creditsToOwner = 0;
        Map<Integer, Integer> pointsToOwner = new LinkedHashMap<>();
        for (ChestStorage.Entry entry : old.entries()) {
            if (entry.kind != ChestStorage.KIND_CURRENCY || entry.quantity <= 0) continue;

            if (entry.type < 0) {
                if (kind == ChestStorage.KIND_CURRENCY) coins = addCapped(coins, entry.quantity);
                else creditsToOwner = addCapped(creditsToOwner, entry.quantity);
            } else {
                pointsToOwner.merge(entry.type, entry.quantity, LegacyChestMigration::addCapped);
            }
        }

        List<FurniRow> furni = new ArrayList<>();
        for (ChestFurniStoredItem stored : old.furniItems()) {
            if (stored.baseItemId <= 0) continue;
            furni.add(new FurniRow(
                    stored.baseItemId,
                    stored.extradata == null ? "" : stored.extradata,
                    stored.limitedStack + ":" + stored.limitedSells));
        }
        List<FurniRow> storedFurni = kind == ChestStorage.KIND_FURNI ? furni : List.of();
        List<FurniRow> furniToOwner = kind == ChestStorage.KIND_FURNI ? List.of() : furni;

        int held = kind == ChestStorage.KIND_FURNI ? storedFurni.size() : coins;
        int level = ChestCapacity.levelCovering(kind, starter, Math.max(old.getCapacityMax(), held));

        // Settings carry over as they were; the capacity rules then follow the new level.
        ChestStorage migrated = ChestStorage.fromStore(old.toSettingsJson(), 0, level, List.of(), kind, starter);
        boolean ownerLoweredCeiling = old.getCapacity() < old.getCapacityMax();
        if (ownerLoweredCeiling) migrated.setCapacity(old.getCapacity());

        return new Plan(
                chest.itemId(),
                chest.ownerId(),
                kind,
                coins,
                migrated.getCapacityLevel(),
                migrated.toSettingsJson(),
                List.copyOf(storedFurni),
                List.copyOf(furniToOwner),
                creditsToOwner,
                Map.copyOf(pointsToOwner));
    }

    /** Write a plan. Oldest stored furni first, so "oldest first" means what it meant before. */
    public static void apply(Plan plan, Sink sink, long firstSequence) {
        sink.createChest(plan.chestId(), plan.kind(), plan.coins(), plan.capacityLevel(), plan.settings());

        long sequence = firstSequence;
        for (FurniRow furni : plan.storedFurni()) {
            sink.storeItem(plan.chestId(), plan.ownerId(), furni, sequence++);
        }
        if (plan.ownerId() <= 0) {
            // Nobody to hand the rest to: keep the old JSON so a hotel owner can still recover it.
            if (!hasRefunds(plan)) sink.clearLegacyData(plan.chestId());
            return;
        }
        for (FurniRow furni : plan.furniToOwner()) sink.giveItem(plan.ownerId(), furni);
        if (plan.creditsToOwner() > 0) sink.giveCredits(plan.ownerId(), plan.creditsToOwner());
        for (Map.Entry<Integer, Integer> points : plan.pointsToOwner().entrySet()) {
            if (points.getValue() > 0) sink.givePoints(plan.ownerId(), points.getKey(), points.getValue());
        }
        sink.clearLegacyData(plan.chestId());
    }

    private static boolean hasRefunds(Plan plan) {
        return !plan.furniToOwner().isEmpty()
                || plan.creditsToOwner() > 0
                || !plan.pointsToOwner().isEmpty();
    }

    private static int addCapped(int a, int b) {
        long sum = (long) a + b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}
