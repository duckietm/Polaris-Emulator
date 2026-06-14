package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class VoucherClaimContractTest {
    private static String voucherSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/Voucher.java"));
    }

    private static String catalogManagerSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/CatalogManager.java"));
    }

    @Test
    void voucherClaimIsSynchronizedAndPersistsBeforeRewardEligibility() throws Exception {
        String source = voucherSource();

        assertTrue(source.contains("public synchronized ClaimResult claimForUser(int userId)"),
                "voucher claim should check limits and persist history under a per-voucher lock");
        assertTrue(source.contains("private boolean insertHistoryEntry"),
                "history insert should report database failure to the caller");

        int insertCall = source.indexOf("insertHistoryEntry(userId, timestamp)");
        int memoryAppend = source.indexOf("this.history.add(new VoucherHistoryEntry(this.id, userId, timestamp))");

        assertTrue(insertCall > -1, "claimForUser must persist the history row");
        assertTrue(memoryAppend > insertCall,
                "in-memory history must only be updated after the database insert succeeds");
    }

    @Test
    void catalogRewardsOnlyAfterVoucherClaimSucceeds() throws Exception {
        String source = catalogManagerSource();

        int claim = source.indexOf("Voucher.ClaimResult claimResult = voucher.claimForUser");
        int claimedGuard = source.indexOf("case CLAIMED", claim);
        int pointsGrant = source.indexOf("client.getHabbo().givePoints", claim);
        int creditsGrant = source.indexOf("client.getHabbo().giveCredits", claim);

        assertTrue(claim > -1, "CatalogManager must claim the voucher before applying rewards");
        assertTrue(claimedGuard > claim, "voucher rewards should only continue for a CLAIMED result");
        assertTrue(pointsGrant > claimedGuard, "points must be granted only after CLAIMED");
        assertTrue(creditsGrant > claimedGuard, "credits must be granted only after CLAIMED");
    }
}
