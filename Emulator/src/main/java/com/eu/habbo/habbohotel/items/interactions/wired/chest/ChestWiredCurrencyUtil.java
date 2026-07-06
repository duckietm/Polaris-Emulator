package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.users.Habbo;

/**
 * Currency helpers shared by wired chest effects and contract transactions.
 */
public final class ChestWiredCurrencyUtil {
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
        if (currencyType < 0) {
            habbo.getHabboInfo().addCredits(-amount);
        } else {
            habbo.getHabboInfo().addCurrencyAmount(currencyType, -amount);
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
