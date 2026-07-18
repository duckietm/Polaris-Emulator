package com.eu.habbo.habbohotel.users;

/**
 * Checked arithmetic for balances persisted in signed INT columns.
 */
public final class WalletBalanceMath {
    private WalletBalanceMath() {
    }

    public static int checkedBalance(int currentBalance, int delta) {
        if (currentBalance < 0) {
            throw new IllegalArgumentException("current balance must not be negative");
        }

        long updated = (long) currentBalance + delta;
        if (updated < 0 || updated > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("wallet update exceeds the supported balance range");
        }

        return (int) updated;
    }

    public static int requireValidBalance(int balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("balance must not be negative");
        }
        return balance;
    }

    /**
     * Clamps {@code currentBalance + delta} into the representable
     * {@code [0, Integer.MAX_VALUE]} range instead of throwing. Used by the
     * legacy add/subtract entry points, which are check-then-act call sites not
     * structured to handle a rejected mutation: clamping keeps the persisted
     * balance within bounds (never negative, never wrapped) while remaining a
     * total function. New code that must reject an out-of-range update should
     * use {@link #checkedBalance(int, int)} instead.
     */
    public static int clampedBalance(int currentBalance, int delta) {
        long updated = (long) Math.max(0, currentBalance) + delta;
        if (updated < 0) {
            return 0;
        }
        if (updated > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) updated;
    }
}
