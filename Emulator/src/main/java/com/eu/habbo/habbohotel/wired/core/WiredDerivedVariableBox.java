package com.eu.habbo.habbohotel.wired.core;

import java.util.List;

/**
 * A wired config-box that, when co-located with a base counter variable, exposes DERIVED read-only
 * sub-variables computed from that counter (e.g. a Quest box exposing {@code counter.is_complete}).
 * {@link WiredVariableLevelSystemSupport} registers these as synthetic read-only variables and reads
 * them through this interface. The level-up box predates the interface and is special-cased; new
 * derived boxes (quest / quest-chain) implement this directly.
 */
public interface WiredDerivedVariableBox {
    /** The selected sub-variable indices this box exposes (0-based). */
    List<Integer> getSelectedSubvariables();

    /** Whether a given sub-variable index is exposed. */
    boolean hasSubvariable(int subType);

    /** The variable-name suffix for a sub-variable (e.g. {@code "is_complete"}). */
    String subvariableKey(int subType);

    /** Compute the derived value for a sub-variable from the base counter value (null base → null). */
    Integer derive(int subType, Integer baseValue);
}
