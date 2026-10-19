package com.eu.habbo.habbohotel.wired.protocol;

import com.eu.habbo.habbohotel.wired.WiredEffectType;
import java.util.Map;

/**
 * Official selector codes (AIR {@code SelectorCodes}, build WIN63-202610131318). Our selectors are effect
 * boxes typed by {@link WiredEffectType}, whose codes share the action space; the selector definition
 * packet carries the official code instead.
 */
public final class WiredSelectorCodes {
    private static final Map<Integer, Integer> OFFICIAL = Map.ofEntries(
            Map.entry(WiredEffectType.FURNI_BYTYPE_SELECTOR.code, 0),
            Map.entry(WiredEffectType.FURNI_PICKS_SELECTOR.code, 1),
            Map.entry(WiredEffectType.USERS_BY_TYPE_SELECTOR.code, 2),
            Map.entry(WiredEffectType.USERS_TEAM_SELECTOR.code, 3),
            Map.entry(WiredEffectType.FURNI_ON_FURNI_SELECTOR.code, 4),
            Map.entry(WiredEffectType.FURNI_SIGNAL_SELECTOR.code, 5),
            Map.entry(WiredEffectType.FURNI_NEIGHBORHOOD_SELECTOR.code, 6),
            Map.entry(WiredEffectType.FURNI_AREA_SELECTOR.code, 7),
            Map.entry(WiredEffectType.USERS_ON_FURNI_SELECTOR.code, 8),
            Map.entry(WiredEffectType.USERS_BY_ACTION_SELECTOR.code, 9),
            Map.entry(WiredEffectType.USERS_SIGNAL_SELECTOR.code, 10),
            Map.entry(WiredEffectType.USERS_BY_NAME_SELECTOR.code, 11),
            Map.entry(WiredEffectType.USERS_NEIGHBORHOOD_SELECTOR.code, 12),
            Map.entry(WiredEffectType.USERS_AREA_SELECTOR.code, 13),
            Map.entry(WiredEffectType.USERS_HANDITEM_SELECTOR.code, 14),
            Map.entry(WiredEffectType.USERS_GROUP_SELECTOR.code, 15),
            Map.entry(WiredEffectType.FURNI_ALTITUDE_SELECTOR.code, 16),
            Map.entry(WiredEffectType.FURNI_WITH_VAR_SELECTOR.code, 17),
            Map.entry(WiredEffectType.USERS_WITH_VAR_SELECTOR.code, 18),
            Map.entry(WiredEffectType.REMOTE_SELECTOR.code, 19));

    private WiredSelectorCodes() {}

    /** The official code of a selector typed {@code effectCode}, or -1 for an effect that is no selector. */
    public static int official(int effectCode) {
        return OFFICIAL.getOrDefault(effectCode, -1);
    }
}
