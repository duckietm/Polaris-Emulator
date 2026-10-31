package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The claim row of a wired reward was written after the reward was delivered, and a failed insert was only logged:
 * a once-only or timed reward could then be claimed again.
 */
class WiredRewardClaimOrderContractTest {

    private static final Path SOURCE = Path.of("src/main/java/com/eu/habbo/habbohotel/wired/core/WiredManager.java");

    @Test
    void theClaimIsWrittenBeforeTheRewardIsDelivered() throws Exception {
        String source = Files.readString(SOURCE);
        int getReward = source.indexOf("public static boolean getReward(");
        int claim = source.indexOf(
                "if (!persistReward(wiredBox.getId(), userId, rewardToGive.id, claimedAt)) {", getReward);
        int deliver = source.indexOf("boolean given = giveReward(habbo, wiredBox, rewardToGive);", getReward);
        int undo = source.indexOf("forgetReward(wiredBox.getId(), userId, rewardToGive.id, claimedAt);", getReward);

        assertTrue(claim > getReward && deliver > claim && undo > deliver);
    }

    @Test
    void aFailedClaimWriteIsReportedAndDeliveryNoLongerWritesIt() throws Exception {
        String source = Files.readString(SOURCE);
        String complete = source.substring(source.indexOf("private static void completeReward("));

        assertTrue(source.contains(
                "private static boolean persistReward(int wiredId, int habboId, int rewardId, int timestamp)"));
        assertTrue(source.contains("return statement.executeUpdate() > 0;"));
        assertFalse(complete.substring(0, complete.indexOf("private static boolean giveReward("))
                .contains("persistReward("));
    }
}
