package com.eu.habbo.messages.incoming.collectibles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.collectibles.CollectibleData;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectibleWardrobeTest {
    private static Product product(String code, int type, List<Integer> figureSetIds) {
        return new Product(code, type, "1", 0, "common", "", figureSetIds, null, 0, false, -1, 0);
    }

    private static final Map<String, Product> PRODUCTS = Map.of(
            "clothing_nftwinterweaves",
                    product("clothing_nftwinterweaves", CollectibleData.TYPE_CLOTHING, List.of(5125)),
            "clothing_nftwinter", product("clothing_nftwinter", CollectibleData.TYPE_CLOTHING, List.of(5988, 5992)),
            "nft_h23_retrococoa", product("nft_h23_retrococoa", CollectibleData.TYPE_FLOOR, List.of(9999)));

    @Test
    void ownedClothingCollectiblesUnlockTheirFigureSets() {
        List<Owned> owned = List.of(
                new Owned(1, 7, "clothing_nftwinterweaves", 1),
                new Owned(2, 7, "nft_h23_retrococoa", 1),
                new Owned(3, 7, "clothing_nftwinter", 4),
                new Owned(4, 7, "clothing_nftwinter", 5),
                new Owned(5, 7, "unknown_product", 1));

        assertEquals(Set.of(5125, 5988, 5992), CollectibleWardrobe.figureSetIds(owned, PRODUCTS, id -> null));
    }

    @Test
    void onlyClothingTokensRefreshTheWardrobe() {
        assertTrue(CollectibleWardrobe.hasClothing(List.of(new Owned(1, 7, "clothing_nftwinter", 1)), PRODUCTS));
        assertFalse(CollectibleWardrobe.hasClothing(List.of(new Owned(1, 7, "nft_h23_retrococoa", 1)), PRODUCTS));
    }
}
