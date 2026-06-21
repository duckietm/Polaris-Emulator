package com.eu.habbo.habbohotel.items.interactions;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RentableSpaceChargeContractTest {
    @Test
    void rentingSpaceChargesCreditsBeforeMarkingSpaceRented() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/items/interactions/InteractionRentableSpace.java"));
        int rentMethod = source.indexOf("public void rent(Habbo habbo)");

        assertTrue(rentMethod >= 0, "InteractionRentableSpace must keep explicit rent handling");

        String rentHandling = source.substring(rentMethod, Math.min(source.length(), rentMethod + 1400));

        assertTrue(rentHandling.contains("int cost = this.rentCost();"),
                "Rent cost must be computed once before charging");
        assertTrue(rentHandling.contains("boolean hasInfiniteCredits = habbo.hasPermission(Permission.ACC_INFINITE_CREDITS);"),
                "Renting must honor infinite-credit staff permission before charging");
        assertTrue(rentHandling.contains("!hasInfiniteCredits && habbo.getHabboInfo().getCredits() < cost"),
                "Renting must reject non-staff users without enough credits for the computed cost");
        assertTrue(rentHandling.contains("habbo.giveCredits(-cost);"),
                "Renting must deduct the computed credit cost");
        assertTrue(rentHandling.indexOf("habbo.giveCredits(-cost);") < rentHandling.indexOf("this.setRenterId"),
                "Credits must be charged before the rentable space is marked as rented");
    }
}
