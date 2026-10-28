package com.eu.habbo.habbohotel.earnings;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** What each income window button does once the server accepts the claim. */
class EarningsClaimPathsContractTest {

    private static final Path MANAGER =
            Path.of("src/main/java/com/eu/habbo/habbohotel/earnings/EarningsCenterManager.java");
    private static final Path HABBO_CLUB =
            Path.of("src/main/java/com/eu/habbo/habbohotel/users/subscriptions/SubscriptionHabboClub.java");

    /** The HC payday row reported success even when no payday row was paid. */
    @Test
    void hcPaydayReportsOnlyAPayoutThatWentThrough() throws Exception {
        String manager = Files.readString(MANAGER);
        String club = Files.readString(HABBO_CLUB);

        assertTrue(manager.contains("return SubscriptionHabboClub.claimUnclaimedPayDays(habbo) > 0;"));
        assertFalse(
                manager.contains("SubscriptionHabboClub.processUnclaimed(habbo)"),
                "the login flow (club gift popup, HC achievement) must not run from the income window");
        assertTrue(club.contains("public static int claimUnclaimedPayDays(Habbo habbo)"));
        assertTrue(club.contains("return paid;"));
        assertTrue(club.contains("claimUnclaimedPayDays(habbo);\n    }"), "the login still pays the payday rows");
    }

    /** An item reward was only written to the database, so it stayed out of the open inventory until relog. */
    @Test
    void anItemRewardLandsInTheOpenInventory() throws Exception {
        String manager = Files.readString(MANAGER);

        assertFalse(manager.contains("INSERT INTO items (user_id, item_id, extra_data)"));
        assertTrue(manager.contains("habbo.getInventory().getItemsComponent().addItems(created);"));
        assertTrue(manager.contains("new AddHabboItemComposer(created)"));
        assertTrue(manager.contains("new InventoryRefreshComposer()"));
    }
}
