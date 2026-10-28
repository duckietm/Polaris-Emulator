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

    /** With the income window on, the payday waits there (as in AIR) instead of paying straight into the purse. */
    @Test
    void hcPaydayWaitsInTheIncomeWindow() throws Exception {
        String club = Files.readString(HABBO_CLUB);

        assertTrue(club.contains("boolean claimed = !waits && claimPayDay(online, totalReward, HC_PAYDAY_CURRENCY);"));
        assertTrue(club.contains("EarningsNotifications.newReward(online, EarningsCategory.HC_PAYDAY);"));
        assertTrue(club.contains("if (paydayWaitsInIncomeWindow()) return;"), "the login leaves waiting paydays alone");
    }

    @Test
    void theWindowCollectsOnlyWhenItAndTheRowAreOn() {
        java.util.Map<String, String> values = new java.util.HashMap<>();
        EarningsCenterManager.ConfigSource config = new EarningsCenterManager.ConfigSource() {
            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
                String value = values.get(key);
                return value == null ? defaultValue : value.equals("1");
            }

            @Override
            public int getInt(String key, int defaultValue) {
                return defaultValue;
            }

            @Override
            public String getValue(String key, String defaultValue) {
                return values.getOrDefault(key, defaultValue);
            }
        };
        EarningsCenterManager.NativeIntegration hcOnly = new EarningsCenterManager.NativeIntegration() {
            @Override
            public boolean handles(EarningsCategory category) {
                return category == EarningsCategory.HC_PAYDAY;
            }

            @Override
            public boolean hasClaim(com.eu.habbo.habbohotel.users.Habbo habbo, EarningsCategory category) {
                return false;
            }

            @Override
            public java.util.List<EarningsReward> rewards(
                    com.eu.habbo.habbohotel.users.Habbo habbo, EarningsCategory category) {
                return java.util.List.of();
            }

            @Override
            public boolean claim(com.eu.habbo.habbohotel.users.Habbo habbo, EarningsCategory category) {
                return false;
            }
        };
        EarningsCenterManager manager =
                new EarningsCenterManager(config, null, null, hcOnly, java.time.Clock.systemUTC());

        assertFalse(manager.collectsInWindow(EarningsCategory.HC_PAYDAY), "the window is off by default");
        values.put("earnings.enabled", "1");
        assertTrue(manager.collectsInWindow(EarningsCategory.HC_PAYDAY));
        assertFalse(manager.collectsInWindow(EarningsCategory.DAILY_GIFT), "a configured row has no own source");
        values.put("earnings.hc_payday.native.enabled", "0");
        assertFalse(manager.collectsInWindow(EarningsCategory.HC_PAYDAY));
        values.remove("earnings.hc_payday.native.enabled");
        values.put("earnings.hc_payday.enabled", "0");
        assertFalse(manager.collectsInWindow(EarningsCategory.HC_PAYDAY));
    }
}
