package com.eu.habbo.habbohotel.items.interactions.wired.chest;

/**
 * How many times a contract negotiation runs, after the official transaction modes of Initiate
 * Transaction (AIR {@code TradeRequirementRules} type):
 *
 * <ul>
 *   <li>{@link #MODE_NORMAL}: once;
 *   <li>{@link #MODE_FIXED}: exactly {@code value} times. The player has to offer the price that many
 *       times over ("the requirements must be met N times") and gets the reward that many times;
 *   <li>{@link #MODE_AUTO}: as many times as the offer covers, at least once and at most {@code value}
 *       ("you can keep adding items to run this payment several times, max N").
 * </ul>
 *
 * The window shows the contract once, plus the multiplier or the auto limit; the server scales.
 */
public record ContractMultiplier(int mode, int value) {
    public static final int MODE_NORMAL = 0;
    public static final int MODE_FIXED = 1;
    public static final int MODE_AUTO = 2;

    /** The official multiplier range (createValueOrVariableSection 1..500). */
    public static final int MIN = 1;

    public static final int MAX = 500;

    public static final ContractMultiplier NONE = new ContractMultiplier(MODE_NORMAL, MIN);

    public ContractMultiplier {
        mode = normalizeMode(mode);
        value = mode == MODE_NORMAL ? MIN : clamp(value);
    }

    /** The highest number of runs this negotiation may settle. */
    public int limit() {
        return this.value;
    }

    public static int normalizeMode(int mode) {
        return mode == MODE_FIXED || mode == MODE_AUTO ? mode : MODE_NORMAL;
    }

    public static int clamp(long value) {
        return (int) Math.max(MIN, Math.min(MAX, value));
    }
}
