package com.eu.habbo.habbohotel.users;

import java.util.Set;

/**
 * Which static chat styles a player may pick, as AIR's chat style selector offers them (RoomChatInputView
 * createOrUpdateChatStylesView over chatstyles_xml): an HC style with Habbo Club, the ambassador style to
 * ambassadors, the plain styles to everyone. A system style (1, 2, 8, 28, 30-36, 38 and the
 * game and notification styles from 120) and an id the library does not hold (18, 39) are never offered.
 * Styles the catalog sells, the hotel's disabled list and staff go through their own checks.
 */
public final class ChatStyleSelection {

    /** chatstyles_xml hcOnly="true". */
    private static final Set<Integer> CLUB_ONLY =
            Set.of(9, 10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22, 24, 25, 26, 27, 29);

    /** chatstyles_xml styles without hcOnly or ambassadorOnly. */
    private static final Set<Integer> EVERYONE = Set.of(0, 3, 4, 5, 6, 7, 23);

    private static final int AMBASSADOR = 37;

    private ChatStyleSelection() {}

    public static boolean isSelectable(int styleId, boolean hasClub, boolean isAmbassador) {
        if (EVERYONE.contains(styleId)) {
            return true;
        }
        if (CLUB_ONLY.contains(styleId)) {
            return hasClub;
        }
        return styleId == AMBASSADOR && isAmbassador;
    }
}
