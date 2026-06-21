package com.eu.habbo.messages.incoming.inventory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestInventoryItemsDeleteContractTest {

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/inventory/RequestInventoryItemsDelete.java"));
    }

    @Test
    void rejectsInvalidDeleteAmountsBeforeMutatingInventory() throws Exception {
        String source = source();
        int amountRead = source.indexOf("int amount = this.packet.readInt()");
        int amountGuard = source.indexOf("amount <= 0 || amount > MAX_DELETE_AMOUNT", amountRead);
        int firstInventoryLookup = source.indexOf("getItemsComponent().getHabboItem", amountRead);

        assertTrue(amountRead > -1, "Delete handler must read the client-provided amount");
        assertTrue(amountGuard > amountRead, "Delete handler must reject non-positive and oversized amounts");
        assertTrue(amountGuard < firstInventoryLookup,
                "Amount validation must happen before inventory lookup or mutation work starts");
    }

    @Test
    void doesNotUseAbsoluteValueForClientProvidedAmount() throws Exception {
        String source = source();

        assertFalse(source.contains("Math.abs(amount)"),
                "Delete amount must not use Math.abs because Integer.MIN_VALUE remains negative");
    }

    @Test
    void deleteLoopUsesValidatedAmountDirectly() throws Exception {
        String source = source();

        assertTrue(source.contains("for (int i = 0; i < amount; i++)"),
                "Delete loop should use the already validated positive amount");
    }
}
