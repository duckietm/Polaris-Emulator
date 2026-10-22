package com.eu.habbo.habbohotel.collectibles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.BoxPrize;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Collection;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.CollectionState;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.LevelReward;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Mintable;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollectorProgressTest {
    private static Product product(String code, int score, String collection) {
        return new Product(code, 1, "18", score, "rare", "", List.of(), collection, 0, false, -1, 0);
    }

    private static Map<String, Product> products() {
        Map<String, Product> products = new LinkedHashMap<>();
        products.put("a", product("a", 100, "set"));
        products.put("b", product("b", 50, "set"));
        products.put("c", product("c", 25, null));
        products.put("reward", product("reward", 10, null));
        products.put("bonus", product("bonus", 10, null));
        return products;
    }

    private static final Collection SET = new Collection("set", "Set", 500, "reward", "bonus", 1000, 2000);

    @Test
    void scoreCountsDistinctProductsAndCompletedSets() {
        Map<String, Product> products = products();
        assertEquals(0, CollectorProgress.score(Map.of(), products, List.of(SET)));
        assertEquals(100, CollectorProgress.score(Map.of("a", 3), products, List.of(SET)));
        assertEquals(125, CollectorProgress.score(Map.of("a", 1, "c", 1), products, List.of(SET)));
        assertEquals(650, CollectorProgress.score(Map.of("a", 1, "b", 2), products, List.of(SET)));
        assertEquals(0, CollectorProgress.score(Map.of("unknown", 1), products, List.of(SET)));
    }

    @Test
    void levelsStartAtOne() {
        assertEquals(1, CollectorProgress.level(0, 1000));
        assertEquals(1, CollectorProgress.level(999, 1000));
        assertEquals(2, CollectorProgress.level(1000, 1000));
        assertEquals(6, CollectorProgress.level(5400, 1000));
        assertEquals(1, CollectorProgress.level(5400, 0));
    }

    @Test
    void anIncompleteSetLocksItsClaims() {
        CollectionState state = CollectorProgress.state(SET, products(), Map.of("a", 1), Map.of(), 1500);
        assertEquals(2, state.entries().size());
        assertEquals(100, state.score());
        assertEquals(150, state.goal());
        assertFalse(state.complete());
        assertEquals(CollectibleData.CLAIM_LOCKED, state.rewardClaim().status());
        assertEquals(CollectibleData.CLAIM_LOCKED, state.bonusClaim().status());
        assertEquals("reward:set", state.rewardClaim().claimId());
        assertEquals("bonus:set", state.bonusClaim().claimId());
    }

    @Test
    void aCompleteSetOpensTheRewardAndTheBonusInsideItsWindow() {
        Map<String, Integer> owned = Map.of("a", 1, "b", 1);
        CollectionState inWindow = CollectorProgress.state(SET, products(), owned, Map.of(), 1500);
        assertTrue(inWindow.complete());
        assertEquals(CollectibleData.CLAIM_AVAILABLE, inWindow.rewardClaim().status());
        assertEquals(CollectibleData.CLAIM_AVAILABLE, inWindow.bonusClaim().status());

        CollectionState late = CollectorProgress.state(SET, products(), owned, Map.of(), 2001);
        assertEquals(CollectibleData.CLAIM_AVAILABLE, late.rewardClaim().status());
        assertEquals(CollectibleData.CLAIM_LOCKED, late.bonusClaim().status());

        CollectionState claimed =
                CollectorProgress.state(SET, products(), owned, Map.of("reward:set", 1, "bonus:set", 1), 1500);
        assertEquals(1, claimed.rewardClaim().claimedAmount());
        assertEquals(CollectibleData.CLAIM_LOCKED, claimed.rewardClaim().status());
        assertEquals(CollectibleData.CLAIM_LOCKED, claimed.bonusClaim().status());
    }

    @Test
    void aSetWithoutRewardsHasNoClaims() {
        Collection plain = new Collection("set", "Set", 0, null, "", 0, 0);
        CollectionState state = CollectorProgress.state(plain, products(), Map.of(), Map.of(), 0);
        assertNull(state.rewardProduct());
        assertNull(state.rewardClaim());
        assertNull(state.bonusClaim());
    }

    @Test
    void levelRewardsHonourLevelAndDates() {
        LevelReward reward = new LevelReward(1, 3, "a", 1, 100, 200);
        assertFalse(CollectorProgress.levelRewardOpen(reward, 2, 150));
        assertTrue(CollectorProgress.levelRewardOpen(reward, 3, 150));
        assertFalse(CollectorProgress.levelRewardOpen(reward, 3, 99));
        assertFalse(CollectorProgress.levelRewardOpen(reward, 3, 201));
        assertTrue(CollectorProgress.levelRewardOpen(new LevelReward(2, 1, "a", 1, 0, 0), 1, 5));
        assertEquals("level:1", reward.claimKey());
    }

    @Test
    void boxDrawsFollowTheWeights() {
        List<BoxPrize> prizes = List.of(new BoxPrize("x", 1), new BoxPrize("none", 0), new BoxPrize("y", 3));
        assertEquals("x", CollectorProgress.draw(prizes, bound -> 0).productCode());
        assertEquals("y", CollectorProgress.draw(prizes, bound -> 1).productCode());
        assertEquals("y", CollectorProgress.draw(prizes, bound -> bound - 1).productCode());
        assertNull(CollectorProgress.draw(List.of(new BoxPrize("none", 0)), bound -> 0));
        assertNull(CollectorProgress.draw(List.of(), bound -> 0));
    }

    @Test
    void emeraldFurniCarryTheirValueInTheName() {
        assertEquals(50, CollectorProgress.emeraldCreditValue("nft_emerald_50"));
        assertEquals(1000, CollectorProgress.emeraldCreditValue("nft_emerald_bar1000"));
        assertEquals(15000, CollectorProgress.emeraldCreditValue("nft_emerald_emerhand"));
        assertEquals(2000, CollectorProgress.emeraldCreditValue("nft_emerald_eggmerald"));
        assertEquals(0, CollectorProgress.emeraldCreditValue("nft_emerald_box"));
        assertEquals(0, CollectorProgress.emeraldCreditValue("CF_50"));
        assertEquals(0, CollectorProgress.emeraldCreditValue(null));
        assertEquals(0, CollectorProgress.emeraldCreditValue("nft_emerald_99999999999"));
    }

    @Test
    void codesAreShortAsciiKeys() {
        assertTrue(CollectiblesService.isValidCode("nft_h23_bear"));
        assertTrue(CollectiblesService.isValidCode("level:3"));
        assertFalse(CollectiblesService.isValidCode(""));
        assertFalse(CollectiblesService.isValidCode(null));
        assertFalse(CollectiblesService.isValidCode("a b"));
        assertFalse(CollectiblesService.isValidCode("x';--"));
        assertFalse(CollectiblesService.isValidCode("a".repeat(65)));
        assertEquals(List.of(1, 2, 3), CollectiblesService.parseIds("1, 2;x;3"));
        assertEquals("hotel-7", CollectiblesService.walletOf(7));
    }

    @Test
    void aRelicWithoutAnEndIsOverAsInAir() {
        assertTrue(CollectiblesService.mintOpen(new Mintable(1, "nft_x", 1, 100, 200), 150));
        assertTrue(CollectiblesService.mintOpen(new Mintable(1, "nft_x", 1, 0, 200), 200));
        assertFalse(CollectiblesService.mintOpen(new Mintable(1, "nft_x", 1, 100, 200), 50));
        assertFalse(CollectiblesService.mintOpen(new Mintable(1, "nft_x", 1, 100, 200), 201));
        assertFalse(CollectiblesService.mintOpen(new Mintable(1, "nft_x", 1, 0, 0), 150));
        assertFalse(CollectiblesService.mintOpen(new Mintable(1, "nft_x", 1, 100, 0), 150));
    }
}
