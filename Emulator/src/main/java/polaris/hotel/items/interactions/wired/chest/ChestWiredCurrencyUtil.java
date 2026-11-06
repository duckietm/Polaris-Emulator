package polaris.hotel.items.interactions.wired.chest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.economy.EconomyOperation;
import polaris.hotel.economy.EconomyOperationId;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;

/**
 * Currency helpers shared by wired chest effects and contract transactions.
 */
public final class ChestWiredCurrencyUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChestWiredCurrencyUtil.class);

    private ChestWiredCurrencyUtil() {}

    public static int getBalance(User user, int currencyType) {
        if (user == null) {
            return 0;
        }
        if (currencyType < 0) {
            return user.getUserInfo().getCredits();
        }
        return user.getUserInfo().getCurrencyAmount(currencyType);
    }

    public static boolean has(User user, int currencyType, int amount) {
        return amount <= 0 || getBalance(user, currencyType) >= amount;
    }

    public static boolean take(User user, int currencyType, int amount) {
        if (user == null || amount <= 0) {
            return amount <= 0;
        }
        if (!has(user, currencyType, amount)) {
            return false;
        }
        int ledgerCurrencyType = currencyType < 0 ? EconomyLedger.CREDITS : currencyType;
        try {
            LedgerWalletMutation.execute(
                    user,
                    new EconomyOperation(
                            EconomyOperationId.create(
                                    "wired-contract:" + user.getUserInfo().getId()),
                            user.getUserInfo().getId(),
                            user.getUserInfo().getId(),
                            "wired_contract_debit",
                            "wired.contract.currency",
                            ledgerCurrencyType,
                            -amount,
                            null,
                            ""));
        } catch (Exception exception) {
            LOGGER.error(
                    "Unable to debit wired contract currency for user {}",
                    user.getUserInfo().getId(),
                    exception);
            return false;
        }
        return true;
    }

    public static void give(User user, int currencyType, int amount) {
        if (user == null || amount <= 0) {
            return;
        }
        if (currencyType < 0) {
            user.giveCredits(amount);
        } else {
            user.givePoints(currencyType, amount);
        }
    }
}
