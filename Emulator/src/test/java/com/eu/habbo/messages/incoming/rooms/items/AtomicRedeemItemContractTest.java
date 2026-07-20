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
        int apply = source.indexOf("LedgerWalletMutation.applyCommitted(", commit);
        int roomRemoval = source.indexOf("room.removeHabboItem(item)");
        int publish = source.indexOf("publishCurrencyGrant(", roomRemoval);

        assertTrue(prepare > -1 && prepare < commit, "plugin currency events must resolve before the transaction");
        assertTrue(commit < apply, "memory balance must use the committed transaction result");
        assertTrue(apply < roomRemoval, "wallet memory must synchronize before later room publication");
        assertTrue(roomRemoval < publish, "client balance publication must follow room removal");
        assertTrue(!source.contains("EconomyAuditLogger.record("),
                "redemption audit must be committed inside the database transaction, not afterwards");
        assertTrue(source.contains("currencyGrant.currencyType()"),
                "audit and balance updates must use the plugin-resolved currency type");
    }

    @Test
    void itemDeleteAndBalanceUpdateShareOneDatabaseTransaction() throws Exception {
        String source = read("src/main/java/com/eu/habbo/messages/incoming/rooms/items/RedeemItemTransaction.java");

        assertTrue(source.contains("setAutoCommit(false)"));
        assertTrue(source.contains("DELETE FROM items WHERE id = ? AND user_id = ? LIMIT 1"));
        assertTrue(source.contains("EconomyLedger.apply(connection"));
        assertTrue(source.contains("\"furniture-redeem:\" + itemId"));
        assertTrue(source.contains("\"furniture.redeem\""));
        assertTrue(source.contains("connection.commit()"));
        assertTrue(source.contains("connection.rollback()"));
    }
}
