package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GuildForumPurchasePolicyTest {
    @Test
    void theOwnerMayAlwaysBuyTheTerminal() {
        assertTrue(GuildForumPurchasePolicy.mayBuy(false, 7, 7, 1));
    }

    @Test
    void aMemberMayBuyAnotherTerminalOnlyWhenTheGroupHasAForum() {
        assertTrue(GuildForumPurchasePolicy.mayBuy(true, 7, 9, 1));
        assertFalse(GuildForumPurchasePolicy.mayBuy(false, 7, 9, 1));
    }

    @Test
    void staffMayBuyForAnyGroup() {
        assertTrue(GuildForumPurchasePolicy.mayBuy(false, 7, 9, 4));
        assertFalse(GuildForumPurchasePolicy.mayBuy(false, 7, 9, 3));
    }

    @Test
    void recognisesTheForumTerminal() {
        assertTrue(GuildForumPurchasePolicy.isForumTerminal("guild_forum"));
        assertFalse(GuildForumPurchasePolicy.isForumTerminal("guild_gate"));
    }
}
