package com.eu.habbo.habbohotel.items.interactions.mysterybox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MysteryBoxPrizesTest {
    @Test
    void aSinglePrizeIsAlwaysTheOneDrawn() {
        List<MysteryBoxPrizes.Prize> pool = List.of(new MysteryBoxPrizes.Prize(7, 1));

        for (int attempt = 0; attempt < 20; attempt++) {
            assertEquals(7, MysteryBoxPrizes.getInstance().draw(pool).catalogItemId());
        }
    }

    @Test
    void everyPrizeOfThePoolCanComeOut() {
        List<MysteryBoxPrizes.Prize> pool = List.of(new MysteryBoxPrizes.Prize(1, 1), new MysteryBoxPrizes.Prize(2, 1));
        boolean sawFirst = false;
        boolean sawSecond = false;

        for (int attempt = 0; attempt < 200; attempt++) {
            int drawn = MysteryBoxPrizes.getInstance().draw(pool).catalogItemId();
            sawFirst |= drawn == 1;
            sawSecond |= drawn == 2;
        }

        assertTrue(sawFirst && sawSecond);
    }

    @Test
    void weightDecidesHowOftenAPrizeComesOut() {
        List<MysteryBoxPrizes.Prize> pool =
                List.of(new MysteryBoxPrizes.Prize(1, 1), new MysteryBoxPrizes.Prize(2, 99));
        int heavy = 0;

        for (int attempt = 0; attempt < 400; attempt++) {
            if (MysteryBoxPrizes.getInstance().draw(pool).catalogItemId() == 2) heavy++;
        }

        assertTrue(heavy > 300, "the heavy prize should dominate, saw " + heavy);
    }
}
