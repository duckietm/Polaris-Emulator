package polaris.hotel.games.lostmonkey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LostMonkeyIslandTest {

    private static final long NOW = 1_000_000_000L;

    @Test
    void theNeedsGoDownAndTheTreesGrowBack() {
        LostMonkeyIsland island = LostMonkeyIsland.fresh(NOW, 3);

        island.harvest(0);
        assertEquals(3 + 9, island.bananas);
        assertThrows(LostMonkeyException.class, () -> island.harvest(0));

        island.advance(NOW + 5 * 3_600_000L);
        assertEquals(30, island.hunger, 0.01);
        assertEquals(LostMonkeyIsland.BUNCHES, island.treeBunches[0]);

        island.feed();
        assertEquals(50, island.hunger, 0.01);
        assertEquals(11, island.bananas);
    }

    @Test
    void sleepingGivesTheEnergyBackAndWakesTheMonkeyUp() {
        LostMonkeyIsland island = LostMonkeyIsland.fresh(NOW, 0);

        island.energy = 10;
        island.sleep();
        assertThrows(LostMonkeyException.class, island::feed);

        island.advance(NOW + 30 * 60_000L);
        assertFalse(island.asleep);
        assertEquals(100, island.energy, 0.01);
    }

    @Test
    void theMonkeyIsNamedOnTheFirstVisit() {
        LostMonkeyIsland island = LostMonkeyIsland.fresh(NOW, 0);

        assertFalse(island.created);
        assertThrows(LostMonkeyException.class, () -> island.create(2, "!"));
        island.create(11, "  Bobo   le singe ");
        assertTrue(island.created);
        assertEquals("Bobo le singe", island.name);
        assertEquals(2, island.colour);
        assertEquals("Bobo le singe", LostMonkeyIsland.fromJson(island.toJson(), NOW).name);
    }

    @Test
    void theRocketIsBuiltPartByPartThenFlies() {
        LostMonkeyIsland island = LostMonkeyIsland.fresh(NOW, 0);

        island.coins = 10_000;
        assertThrows(LostMonkeyException.class, island::launch);

        for (int part = 0; part < LostMonkeyIsland.ROCKET_READY; part++) island.buildRocket();

        assertThrows(LostMonkeyException.class, island::buildRocket);
        island.launch();
        assertEquals(0, island.rocket);
        assertEquals(1, island.launches);
    }

    @Test
    void theGamesCoinsHaveADailyLimitAndTheIslandIsSaved() {
        LostMonkeyIsland island = LostMonkeyIsland.fresh(NOW, 0);
        int before = island.coins;
        int won = 0;

        for (int game = 0; game < 10; game++) won += island.reward(5000, 7);

        assertEquals(LostMonkeyIsland.DAILY_COINS, won);
        assertEquals(before + won, island.coins);
        assertEquals(80, island.reward(5000, 8));

        LostMonkeyIsland again = LostMonkeyIsland.fromJson(island.toJson(), NOW);

        assertEquals(island.coins, again.coins);
        assertTrue(again.view(NOW).has("parts"));
    }
}
