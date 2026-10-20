package com.eu.habbo.habbohotel.wired.protocol;

/**
 * A quantified condition. Its "all" / "any" choice travels in the official quantifier slots, never in its int
 * params: the save packet's quantifier code sets it and the definition's quantifier code reports it, 0 for all and 1
 * for any. {@link WiredEditorDefaults} gives the footer type and whether its labels read as the negative form.
 */
public interface WiredOfficialQuantifier {
    int officialQuantifierCode();
}
