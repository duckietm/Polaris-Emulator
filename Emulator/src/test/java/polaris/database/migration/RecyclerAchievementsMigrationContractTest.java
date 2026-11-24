package polaris.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The Furni-Matic achievements the emulator progresses are defined by a migration, under the names it uses. */
class RecyclerAchievementsMigrationContractTest {
    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20261124110000__recycler_achievements.sql");

    @Test
    void everyAchievementTheEmulatorProgressesIsDefinedOnlyWhereTheHotelHasNone() throws Exception {
        String sql = Files.readString(MIGRATION);

        for (String name : new String[] {
            "RecycledItems", "RecycleBoxOpener", "RecycleEcoprizes", "RecyclerRarePrize", "RecyclerGrandPrize"
        }) {
            assertTrue(sql.contains("SELECT '" + name + "' AS `name`, 1 AS `level`"), name);
            assertTrue(sql.contains("WHERE present.`name` = '" + name + "');"), name);
        }

        assertEquals(5, sql.split("INSERT INTO `achievements`", -1).length - 1);
    }
}
