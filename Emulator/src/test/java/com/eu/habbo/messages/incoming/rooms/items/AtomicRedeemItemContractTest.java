package com.eu.habbo.messages.incoming.rooms.items;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicRedeemItemContractTest {
    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }

    @Test
    void pluginResolutionPrecedesTransactionAndMemoryApplyFollowsCommit() throws Exception {
        String source = read("src/main/java/com/eu/habbo/messages/incoming/rooms/items/RedeemItemEvent.java");
        int prepare = source.indexOf("prepareCurrencyGrant(");
        int commit = source.indexOf("RedeemItemTransaction.commit(");
        int roomRemoval = source.indexOf("room.removeHabboItem(item)");
        int apply = source.indexOf("applyCurrencyGrant(");
        int audit = source.indexOf("EconomyAuditLogger.record(");

        assertTrue(prepare > -1 && prepare < commit, "plugin currency events must resolve before the transaction");
        assertTrue(commit < roomRemoval, "database commit must precede room removal");
        assertTrue(roomRemoval < apply, "memory balance must be synchronized only after commit");
        assertTrue(apply < audit, "economy audit must record the committed and synchronized balance");
        assertTrue(source.contains("currencyGrant.currencyType()"),
                "audit and balance updates must use the plugin-resolved currency type");
    }

    @Test
    void itemDeleteAndBalanceUpdateShareOneDatabaseTransaction() throws Exception {
        String source = read("src/main/java/com/eu/habbo/messages/incoming/rooms/items/RedeemItemTransaction.java");

        assertTrue(source.contains("setAutoCommit(false)"));
        assertTrue(source.contains("DELETE FROM items WHERE id = ? AND user_id = ? LIMIT 1"));
        assertTrue(source.contains("UPDATE users SET credits = credits + ? WHERE id = ? LIMIT 1"));
        assertTrue(source.contains("ON DUPLICATE KEY UPDATE amount = amount + ?"));
        assertTrue(source.contains("connection.commit()"));
        assertTrue(source.contains("connection.rollback()"));
    }
}
