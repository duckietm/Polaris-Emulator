package com.eu.habbo.habbohotel.catalog;

/**
 * Checked arithmetic for values that cross the catalog-to-wallet boundary.
 */
public final class CatalogPurchaseMath {
    private static final int SECONDS_PER_DAY = 86_400;

    private CatalogPurchaseMath() {
    }

    public static int checkedPrice(int unitPrice, int payableUnits) {
        return checkedMultiply(unitPrice, payableUnits, "catalog price");
    }

    public static int checkedAdd(int left, int right) {
        requireNonNegative(left, "catalog total");
        requireNonNegative(right, "catalog surcharge");

        long total = (long) left + right;
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("catalog total exceeds the supported wallet range");
        }

        return (int) total;
    }

    public static int checkedSubscriptionSeconds(int days) {
        return checkedMultiply(days, SECONDS_PER_DAY, "subscription duration");
    }

    public static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }

        return value;
    }

    private static int checkedMultiply(int left, int right, String name) {
        requireNonNegative(left, name);
        requireNonNegative(right, name + " multiplier");

        long result = (long) left * right;
        if (result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " exceeds the supported wallet range");
        }

        return (int) result;
    }
}
