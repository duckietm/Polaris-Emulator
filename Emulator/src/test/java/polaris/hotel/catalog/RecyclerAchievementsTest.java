package polaris.hotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecyclerAchievementsTest {
    @Test
    void everyOpenedBoxCountsAsABoxAndAPrize() {
        assertEquals(List.of("RecycleBoxOpener", "RecycleEcoprizes"), RecyclerAchievements.forOpenedBox(1));
        assertEquals(List.of("RecycleBoxOpener", "RecycleEcoprizes"), RecyclerAchievements.forOpenedBox(2));
        // A prize that is no longer in the prize list still came out of a box.
        assertEquals(List.of("RecycleBoxOpener", "RecycleEcoprizes"), RecyclerAchievements.forOpenedBox(0));
    }

    @Test
    void aRareAndAGrandPrizeCountOnTop() {
        assertEquals(
                List.of("RecycleBoxOpener", "RecycleEcoprizes", "RecyclerRarePrize"),
                RecyclerAchievements.forOpenedBox(3));
        assertEquals(
                List.of("RecycleBoxOpener", "RecycleEcoprizes", "RecyclerRarePrize"),
                RecyclerAchievements.forOpenedBox(4));
        assertEquals(
                List.of("RecycleBoxOpener", "RecycleEcoprizes", "RecyclerRarePrize", "RecyclerGrandPrize"),
                RecyclerAchievements.forOpenedBox(5));
        assertEquals(4, RecyclerAchievements.forOpenedBox(6).size());
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/" + path));
    }

    @Test
    void theHandlersCountOnlyWhatReallyHappened() throws Exception {
        String recycle = source("polaris/messages/incoming/catalog/recycler/RecycleEvent.java");
        // The furni count once the box exists, not when the exchange is asked for.
        assertTrue(recycle.indexOf("RecyclerAchievements.recycled(") > recycle.indexOf("consumed = true;"));

        String open = source("polaris/messages/incoming/catalog/recycler/OpenRecycleBoxEvent.java");
        int opened = open.indexOf("case OPENED ->");
        assertTrue(opened > 0);
        // Only an opened box with a prize counts: not an empty one, not a failed opening.
        assertTrue(open.contains("RecyclerAchievements.boxOpened(this.client.getUser(), reward.getBaseItem());"));
        assertEquals(1, open.split("RecyclerAchievements\\.boxOpened\\(", -1).length - 1);
    }
}
