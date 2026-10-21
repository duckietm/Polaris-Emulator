package com.eu.habbo.habbohotel.catalog.specialitems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpecialItemClaimServiceIT {
    private static HikariDataSource dataSource;
    private SpecialItemClaimService service;
    private final AtomicInteger created = new AtomicInteger();
    private boolean factoryFails;

    @BeforeAll
    static void migrate() {
        if (!TestDatabase.dockerAvailable()) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                fail("Docker is required in CI");
            }
            assumeTrue(false, "Docker is not available");
        }
        dataSource = TestDatabase.freshDatabase("special_items");
        MigrationRunner.migrate(dataSource);
    }

    @AfterAll
    static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void reset() throws Exception {
        created.set(0);
        factoryFails = false;
        Item base = mock(Item.class);
        service = new SpecialItemClaimService(dataSource, id -> id == 18 ? base : null, (userId, itemId) -> {
            if (factoryFails) return null;
            created.incrementAndGet();
            return mock(HabboItem.class);
        });
        execute(
                "DELETE FROM users_special_item_claims",
                "DELETE FROM special_items_free_claims WHERE claim_id <> 'chests_starter'",
                "INSERT INTO special_items_free_claims (claim_id, item_id, enabled) VALUES ('it_on', 18, 1), ('it_off', 18, 0), ('it_gone', 999999, 1)");
    }

    @Test
    void migrationOffersTheStarterChestWhenTheHotelHasIt() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery(
                        "SELECT c.item_id, b.item_name FROM special_items_free_claims c JOIN items_base b ON b.id = c.item_id WHERE c.claim_id = 'chests_starter'")) {
            if (set.next()) {
                assertEquals("wf_storage_furni_starter", set.getString("item_name"));
                assertTrue(service.findOffer("chests_starter").isPresent());
            }
        }
    }

    @Test
    void onlyEnabledOffersAreClaimable() throws Exception {
        assertEquals(18, service.findOffer("it_on").orElseThrow().itemId());
        assertTrue(service.findOffer("it_off").isEmpty());
        assertTrue(service.findOffer("unknown").isEmpty());
        assertTrue(service.findOffer("bad id").isEmpty());
    }

    @Test
    void aClaimIsRecordedOncePerUser() throws Exception {
        SpecialItemClaimService.Offer offer = service.findOffer("it_on").orElseThrow();

        assertFalse(service.hasClaimed(910101, "it_on"));
        assertTrue(service.recordClaim(910101, offer, 100));
        assertFalse(service.recordClaim(910101, offer, 101));
        assertTrue(service.hasClaimed(910101, "it_on"));
        assertFalse(service.hasClaimed(910102, "it_on"));
        assertTrue(service.recordClaim(910102, offer, 102));

        service.forgetClaim(910101, "it_on");
        assertFalse(service.hasClaimed(910101, "it_on"));
    }

    @Test
    void claimGivesTheFurniOnceAndAnswersClaimed() throws Exception {
        assertFalse(service.claimedOrUnavailable(910103, "it_on"));
        assertTrue(service.claimedOrUnavailable(910103, "it_off"));

        SpecialItemClaimService.ClaimResult first = service.claim(910103, "it_on", 200);
        assertTrue(first.claimed());
        assertTrue(first.item() != null);

        SpecialItemClaimService.ClaimResult second = service.claim(910103, "it_on", 201);
        assertTrue(second.claimed());
        assertEquals(null, second.item());
        assertEquals(1, created.get());
        assertTrue(service.claimedOrUnavailable(910103, "it_on"));
    }

    @Test
    void unknownDisabledOrBrokenClaimsGiveNothing() throws Exception {
        assertEquals(null, service.claim(910104, "it_off", 1).item());
        assertEquals(null, service.claim(910104, "nope", 1).item());
        assertEquals(null, service.claim(910104, "it_gone", 1).item());
        assertFalse(service.hasClaimed(910104, "it_gone"));

        factoryFails = true;
        SpecialItemClaimService.ClaimResult failed = service.claim(910104, "it_on", 1);
        assertFalse(failed.claimed());
        assertFalse(service.hasClaimed(910104, "it_on"));
        assertEquals(0, created.get());
    }

    private static void execute(String... sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            for (String query : sql) {
                statement.execute(query);
            }
        }
    }
}
