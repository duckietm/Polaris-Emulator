package com.eu.habbo.habbohotel.collectibles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A set claim takes one token of each product that did not already earn it on another account. */
class CollectibleClaimAssetsTest {

    private static Product product(String code) {
        Product product = mock(Product.class);
        when(product.productCode()).thenReturn(code);
        return product;
    }

    @Test
    void picksOneUnspentTokenOfEachProduct() {
        List<Owned> owned = List.of(new Owned(1, 7, "bear", 1), new Owned(2, 7, "bear", 2), new Owned(3, 7, "duck", 1));

        assertEquals(
                List.of(2L, 3L),
                CollectiblesService.pickAssets(owned, Set.of(1L), List.of(product("bear"), product("duck"))));
    }

    @Test
    void aSetWhoseOnlyTokenIsSpentCannotBeClaimed() {
        List<Owned> owned = List.of(new Owned(1, 7, "bear", 1), new Owned(3, 7, "duck", 1));

        assertNull(CollectiblesService.pickAssets(owned, Set.of(3L), List.of(product("bear"), product("duck"))));
        assertNull(CollectiblesService.pickAssets(owned, Set.of(), List.of()));
    }

    @Test
    void spentTokensDoNotCount() {
        List<Owned> owned = List.of(new Owned(1, 7, "bear", 1), new Owned(3, 7, "duck", 1));

        assertEquals(List.of(owned.get(0)), CollectiblesService.unspent(owned, Set.of(3L)));
    }
}
