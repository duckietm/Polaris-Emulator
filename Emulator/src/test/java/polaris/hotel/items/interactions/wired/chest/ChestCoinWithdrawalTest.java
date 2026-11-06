package polaris.hotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

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
        this.previousPayer = ChestWithdrawals.usePayer((user, chestId, credits) -> {
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

    private static User owner() {
        User user = mock(User.class);
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(OWNER);
        when(info.getUsername()).thenReturn("Owner");
        when(user.getUserInfo()).thenReturn(info);
        return user;
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
