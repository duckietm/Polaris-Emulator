package com.eu.habbo.messages.rcon;

public class RconGrantGuard {
    public static final int DEFAULT_MAX_AMOUNT = 1_000_000;

    private RconGrantGuard() {
    }

    public static String validateUserId(int userId) {
        return userId > 0 ? null : "invalid user";
    }

    public static String validatePositiveAmount(int amount, int maxAmount, String fieldName) {
        if (amount <= 0) {
            return "invalid " + fieldName;
        }

        if (maxAmount > 0 && amount > maxAmount) {
            return fieldName + " exceeds rcon grant ceiling";
        }

        return null;
    }

    public static String validateNonNegativeAmount(int amount, int maxAmount, String fieldName) {
        if (amount < 0) {
            return "invalid " + fieldName;
        }

        if (maxAmount > 0 && amount > maxAmount) {
            return fieldName + " exceeds rcon grant ceiling";
        }

        return null;
    }

    public static String validateCurrencyType(int type) {
        return type >= 0 ? null : "invalid currency type";
    }

    public static int parseMaxAmount(String rawValue) {
        try {
            int value = Integer.parseInt(rawValue);
            return value > 0 ? value : DEFAULT_MAX_AMOUNT;
        } catch (Exception e) {
            return DEFAULT_MAX_AMOUNT;
        }
    }
}
