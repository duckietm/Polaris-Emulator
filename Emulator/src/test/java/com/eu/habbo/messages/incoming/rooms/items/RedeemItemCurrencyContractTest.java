package com.eu.habbo.messages.incoming.rooms.items;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RedeemItemCurrencyContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/rooms/items/RedeemItemEvent.java"));
    }

    @Test
    void diamondFurnitureCreditsTheDiamondCurrencyExplicitly() throws Exception {
        String source = source();
        int diamondFurniture = source.indexOf("startsWith(\"CF_diamond_\")");
        int diamondType = source.indexOf("FurnitureRedeemedEvent.DIAMONDS", diamondFurniture);
        int transaction = source.indexOf("RedeemItemTransaction.commit(", diamondType);
        int transactionType = source.indexOf("currencyGrant.currencyType()", transaction);
        int apply = source.indexOf("LedgerWalletMutation.applyCommitted(", transactionType);
        int committedBalance = source.indexOf("mutation.balanceAfter()", apply);
        int publish = source.indexOf("publishCurrencyGrant(currencyGrant)", committedBalance);

        assertTrue(diamondFurniture > -1 && diamondType > diamondFurniture,
                "diamond furniture must resolve explicitly to the diamond currency type");
        assertTrue(transactionType > transaction,
                "the resolved diamond type must be persisted by the atomic transaction");
        assertTrue(apply > transactionType,
                "the client balance must use the transaction's committed wallet result");
        assertTrue(committedBalance > apply);
        assertTrue(publish > committedBalance);
    }
}
