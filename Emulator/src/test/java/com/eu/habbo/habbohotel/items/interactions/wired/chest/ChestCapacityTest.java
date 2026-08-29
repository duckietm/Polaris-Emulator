package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A chest has two ceilings and they mean different things.
 *
 * <p>{@code capacityMax} is what the owner has bought. {@code capacity} is what they have chosen to
 * use of it — buying more room and deciding to fill it are two decisions, and a chest can be big and
 * still be told to stop early. Everything that puts something in measures against the second.
 */
class ChestCapacityTest {

    private static ChestStorage chestHolding(int coins) {
        ChestStorage storage = new ChestStorage();
        storage.add(ChestStorage.KIND_CURRENCY, -1, coins);
        return storage;
    }

    @Test
    void depositsStopAtTheCeilingTheOwnerSetRatherThanTheOneTheyBought() {
        ChestStorage storage = new ChestStorage();
        storage.setCapacityMax(50_000);
        storage.setCapacity(100);

        int accepted = storage.depositCurrency(-1, 500);

        assertEquals(100, accepted, "the chest took more than its owner told it to hold");
        assertEquals(100, storage.total(ChestStorage.KIND_CURRENCY));
    }

    @Test
    void theCeilingCanNeverExceedWhatWasBought() {
        ChestStorage storage = new ChestStorage();
        storage.setCapacity(ChestStorage.MAX_CAPACITY);

        assertEquals(
                storage.getCapacityMax(),
                storage.getCapacity(),
                "a chest cannot be told to hold more than its owner paid for");
    }

    @Test
    void loweringTheCeilingBelowWhatIsInsideKeepsTheContents() {
        ChestStorage storage = chestHolding(4_000);

        storage.setCapacity(10);

        assertEquals(4_000, storage.total(ChestStorage.KIND_CURRENCY), "lowering a ceiling is not a delete");
        assertEquals(0, storage.depositCurrency(-1, 1), "but nothing more goes in while it is over the line");
    }

    @Test
    void aChestWithNoCeilingOfItsOwnUsesEverythingItHas() {
        ChestStorage storage = new ChestStorage();
        storage.setCapacityMax(20_000);

        assertTrue(
                storage.getCapacity() > 0 && storage.getCapacity() <= storage.getCapacityMax(),
                "a chest that was never given a ceiling still has to have one");
    }

    @Test
    void buyingRoomAlsoRaisesACeilingTheOwnerWasNotHoldingBack() {
        ChestStorage storage = new ChestStorage();
        int before = storage.getCapacity();

        storage.growCapacity(ChestStorage.CAPACITY_STEP);

        assertEquals(
                before + ChestStorage.CAPACITY_STEP,
                storage.getCapacity(),
                "paying to enlarge a chest that was already using all its room must enlarge it");
    }

    @Test
    void buyingRoomLeavesACeilingTheOwnerDeliberatelyLowered() {
        ChestStorage storage = new ChestStorage();
        storage.setCapacity(100);

        storage.growCapacity(ChestStorage.CAPACITY_STEP);

        assertEquals(100, storage.getCapacity(), "a ceiling set by hand is a decision, not a leftover");
        assertEquals(
                ChestStorage.DEFAULT_CAPACITY + ChestStorage.CAPACITY_STEP,
                storage.getCapacityMax(),
                "the room bought is still there to be used later");
    }
}
