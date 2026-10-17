package com.eu.habbo.habbohotel.catalog;

/**
 * Who may buy a group forum terminal (item "guild_forum") for a group they are a member of.
 *
 * <p>The official catalog widget offers a group when it already has a forum (the terminal is then just another way into it), when
 * the buyer owns it, or to staff (security level 4 and up) for any group. A purchase for a group without a forum turns it on.
 */
public final class GuildForumPurchasePolicy {
    static final String FORUM_ITEM_NAME = "guild_forum";
    static final int STAFF_SECURITY_LEVEL = 4;

    private GuildForumPurchasePolicy() {}

    public static boolean isForumTerminal(String itemName) {
        return FORUM_ITEM_NAME.equals(itemName);
    }

    public static boolean mayBuy(boolean guildHasForum, int guildOwnerId, int buyerId, int buyerRankLevel) {
        return guildHasForum || guildOwnerId == buyerId || buyerRankLevel >= STAFF_SECURITY_LEVEL;
    }
}
