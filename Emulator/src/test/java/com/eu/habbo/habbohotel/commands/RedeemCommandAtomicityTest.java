package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * :redeem has to be all or nothing per furni, like the redeem packet. It used to hand
 * over every currency first and queue the deletes on the persistence pool, so anything
 * between the two - an error, a shutdown - left the user with both the credits and the
 * furni.
 */
class RedeemCommandAtomicityTest {

    private static final Path SOURCE = Path.of("src/main/java/com/eu/habbo/habbohotel/commands/RedeemCommand.java");

    private static String source() throws Exception {
        return Files.readString(SOURCE);
    }

    @Test
    void itUsesTheSameTransactionTheRedeemPacketUses() throws Exception {
        String source = source();

        assertTrue(source.contains("RedeemItemTransaction.commit("), "the delete and the payment share a transaction");
        assertTrue(
                source.contains("LedgerWalletMutation.applyCommitted("),
                "the in-memory balance follows the committed result");
    }

    @Test
    void nothingIsGivenOutsideThatTransaction() throws Exception {
        String source = source();

        assertFalse(source.contains("giveCredits("), "credits come from the transaction now:\n" + source);
        assertFalse(source.contains("givePixels("), "pixels come from the transaction now:\n" + source);
        assertFalse(source.contains("givePoints("), "points come from the transaction now:\n" + source);
        assertFalse(
                source.contains("QueryDeleteHabboItems"),
                "the furni is deleted inside the transaction, not queued for later:\n" + source);
    }

    @Test
    void aFurniThatDidNotCommitIsNeitherRemovedNorCounted() throws Exception {
        String source = source();

        int commit = source.indexOf("RedeemItemTransaction.commit(");
        int bail = source.indexOf("if (mutation == null) continue;", commit);
        int removal = source.indexOf("removeHabboItem(redeemable.item())", commit);
        int count = source.indexOf("addRedeemPoints(redeemed", commit);

        assertTrue(bail > commit, "a commit that did not happen stops this furni:\n" + source);
        assertTrue(bail < removal, "the furni leaves the inventory only after its commit");
        assertTrue(bail < count, "only what committed is reported to the user");
    }
}
