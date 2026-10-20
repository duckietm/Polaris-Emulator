package com.eu.habbo.habbohotel.wired.core;

/**
 * Folds the per-target results of a condition's "all" / "any" quantifier the way the official
 * server does. "All of them match" holds over an empty selection and "any of them matches" does
 * not (official client capture of Has Variable and Variable Value); a "not" twin flips the result,
 * so its "all" fails and its "any" passes on an empty selection.
 */
public final class WiredQuantifier {
    private WiredQuantifier() {}

    /** A positive condition's result over an empty selection. */
    public static boolean overEmpty(boolean requireAll) {
        return requireAll;
    }

    /** A "not" twin's result over an empty selection: the positive result, flipped. */
    public static boolean overEmptyNegated(boolean requireAll) {
        return !requireAll;
    }

    /** Every result when {@code requireAll}, otherwise at least one; "all" holds over none. */
    public static boolean fold(Iterable<Boolean> results, boolean requireAll) {
        boolean any = false;
        for (Boolean result : results) {
            boolean matched = Boolean.TRUE.equals(result);
            if (requireAll && !matched) {
                return false;
            }
            any |= matched;
        }
        return requireAll || any;
    }
}
