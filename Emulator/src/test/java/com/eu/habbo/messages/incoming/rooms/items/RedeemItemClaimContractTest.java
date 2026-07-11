package com.eu.habbo.messages.incoming.rooms.items;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RedeemItemClaimContractTest {
    private static String eventSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/RedeemItemEvent.java"));
    }

    private static String claimSource() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/RedeemItemClaim.java"));
    }

    @Test
    void databaseClaimPrecedesRoomRemovalAndCurrencyGrant() throws Exception {
        String source = eventSource();
        int claim = source.indexOf("RedeemItemClaim.tryClaim(");
        int roomRemoval = source.indexOf("room.removeHabboItem(item)");
        int currencyGrant = source.indexOf("switch (furniRedeemEvent.currencyID)");

        assertTrue(claim > -1, "redemption must claim the item in the database");
        assertTrue(claim < roomRemoval, "database claim must happen before room removal");
        assertTrue(claim < currencyGrant, "database claim must happen before currency grant");
    }

    @Test
    void claimRequiresBothItemAndOwnerAndChecksAffectedRows() throws Exception {
        String source = claimSource();

        assertTrue(source.contains("DELETE FROM items WHERE id = ? AND user_id = ? LIMIT 1"));
        assertTrue(source.contains("executeUpdate() == 1"),
                "only the request that deletes the owned item may receive currency");
    }
}
