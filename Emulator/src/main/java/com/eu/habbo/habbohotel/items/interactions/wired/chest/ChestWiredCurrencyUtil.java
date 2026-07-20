package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.economy.EconomyLedger;
import com.eu.habbo.habbohotel.economy.EconomyOperation;
import com.eu.habbo.habbohotel.economy.EconomyOperationId;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.LedgerWalletMutation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Currency helpers shared by wired chest effects and contract transactions.
 */
public final class ChestWiredCurrencyUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChestWiredCurrencyUtil.class);

    private ChestWiredCurrencyUtil() {
    }

    public static int getBalance(Habbo habbo, int currencyType) {
        if (habbo == null) {
            return 0;
        }
        if (currencyType < 0) {
            return habbo.getHabboInfo().getCredits();
        }
        return habbo.getHabboInfo().getCurrencyAmount(currencyType);
    }

    public static boolean has(Habbo habbo, int currencyType, int amount) {
        return amount <= 0 || getBalance(habbo, currencyType) >= amount;
    }

    public static boolean take(Habbo habbo, int currencyType, int amount) {
        if (habbo == null || amount <= 0) {
            return amount <= 0;
        }
        if (!has(habbo, currencyType, amount)) {
            return false;
        }
        int ledgerCurrencyType =
                currencyType < 0 ? EconomyLedger.CREDITS : currencyType;
        try {
            LedgerWalletMutation.execute(habbo, new EconomyOperation(
                    EconomyOperationId.create(
                            "wired-contract:" + habbo.getHabboInfo().getId()),
                    habbo.getHabboInfo().getId(),
                    habbo.getHabboInfo().getId(),
                    "wired_contract_debit",
                    "wired.contract.currency",
                    ledgerCurrencyType,
                    -amount,
                    null,
                    ""));
        } catch (Exception exception) {
            LOGGER.error("Unable to debit wired contract currency for user {}",
                    habbo.getHabboInfo().getId(), exception);
            return false;
        }
        return true;
    }

    public static void give(Habbo habbo, int currencyType, int amount) {
        if (habbo == null || amount <= 0) {
            return;
        }
        if (currencyType < 0) {
            habbo.giveCredits(amount);
        } else {
            habbo.givePoints(currencyType, amount);
        }
    }
}
