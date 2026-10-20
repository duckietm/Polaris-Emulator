package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A player's own coin withdrawal ("Opnemen", "Alles opnemen"): the coins leave the chest and reach the purse
 * through the economy ledger, and when the purse refuses them they go back into the chest instead of vanishing.
 */
class ChestCoinWithdrawalTest {
    private static final int OWNER = 2;

    private InMemoryChestStore store;
    private ChestStore previousStore;
    private ChestWithdrawals.CreditPayer previousPayer;
    private final List<Integer> paid = new ArrayList<>();
    private boolean purseAccepts = true;

    @BeforeEach
    void setUp() {
        this.store = new InMemoryChestStore();
        this.previousStore = ChestStores.use(this.store);
        this.previousPayer = ChestWithdrawals.usePayer((habbo, chestId, credits) -> {
            if (!this.purseAccepts) return false;
            this.paid.add(credits);
            return true;
        });
    }

    @AfterEach
    void tearDown() {
        ChestWithdrawals.usePayer(this.previousPayer);
        ChestStores.use(this.previousStore);
    }

    private static Habbo owner() {
        Habbo habbo = mock(Habbo.class);
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(OWNER);
        when(info.getUsername()).thenReturn("Owner");
        when(habbo.getHabboInfo()).thenReturn(info);
        return habbo;
    }

    private InteractionWiredChestCurrency chestWith(int coins) {
        InteractionWiredChestCurrency chest = new InteractionWiredChestCurrency(60, OWNER, mock(Item.class), "", 0, 0);
        assertEquals(coins, chest.addCoins(coins));
        return chest;
    }

    @Test
    void anAmountAndEverythingGoIntoThePurse() {
        InteractionWiredChestCurrency chest = this.chestWith(500);

        ChestWithdrawals.Result some =
                ChestWithdrawals.coins(owner(), chest, null, 120, ChestTransactionLog.SOURCE_USER);
        ChestWithdrawals.Result rest =
                ChestWithdrawals.coins(owner(), chest, null, -1, ChestTransactionLog.SOURCE_USER);

        assertEquals(120, some.coins());
        assertEquals(380, rest.coins());
        assertEquals(List.of(120, 380), this.paid);
        assertEquals(0, chest.getContents().getCoins());
        assertEquals(0, this.store.chests.get(60).coins());
    }

    @Test
    void coinsThePurseRefusesStayInTheChest() {
        InteractionWiredChestCurrency chest = this.chestWith(500);
        this.purseAccepts = false;

        ChestWithdrawals.Result result =
                ChestWithdrawals.coins(owner(), chest, null, 200, ChestTransactionLog.SOURCE_USER);

        assertTrue(result.isEmpty(), "nothing was withdrawn");
        assertEquals(500, chest.getContents().getCoins());
        assertEquals(500, this.store.chests.get(60).coins(), "the stored balance is whole again");
    }
}
