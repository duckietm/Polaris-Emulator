package com.eu.habbo.habbohotel.wired.protocol;

/**
 * A condition whose "all" / "any" choice the official definition shows in its quantifier footer. The choice itself
 * stays in the box's own int params; this only reports it, 0 for all and 1 for any.
 */
public interface WiredOfficialQuantifier {
    int officialQuantifierCode();
}
