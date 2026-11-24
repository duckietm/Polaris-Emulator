package polaris.database.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The Furni-Matic settings arrive with the migrations, so a hotel has nothing to run by hand. */
class RecyclerSettingsMigrationContractTest {
    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V20261124090000__recycler_cost_wait_and_box.sql");

    @Test
    void theRowsCarryTheDefaultsTheEmulatorUsesAndKeepAHotelsOwnValues() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("('recycler.ducket_cost', '0',"));
        assertTrue(sql.contains("('recycler.timeout_seconds', '0',"));
        assertTrue(sql.contains("('hotel.ecotron.box', 'ecotron_box',"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE `value` = `value`;"));
    }

    @Test
    void theMysteryBoxRulesOnlyApplyWhereNobodyOwnsOne() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("WHERE base.`item_name` = 'matic_box'"));
        assertTrue(sql.contains("AND NOT EXISTS (SELECT 1 FROM `items` owned WHERE owned.`item_id` = base.`id`)"));
        // Quoted: the flag columns are ENUM('0','1') in older hotels, where a bare 0 is refused as truncated data.
        assertTrue(sql.contains(
                "SET base.`allow_trade` = '0', base.`allow_marketplace_sell` = '0', base.`allow_gift` = '0',"));
        assertFalse(sql.contains("= 0,"));
        // The classic box is left as the hotel has it.
        assertFalse(sql.contains("= 'ecotron_box'\n"));
    }
}
