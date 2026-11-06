package polaris.hotel.items.interactions;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RentableSpaceChargeContractTest {
    @Test
    void rentingSpaceChargesCreditsBeforeMarkingSpaceRented() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/polaris/hotel/items/interactions/InteractionRentableSpace.java"));
        int rentMethod = source.indexOf("public void rent(User user)");

        assertTrue(rentMethod >= 0, "InteractionRentableSpace must keep explicit rent handling");

        String rentHandling = source.substring(rentMethod, Math.min(source.length(), rentMethod + 1400));

        assertTrue(
                rentHandling.contains("int cost = this.rentCost();"),
                "Rent cost must be computed once before charging");
        assertTrue(
                rentHandling.contains(
                        "boolean hasInfiniteCredits = user.hasPermission(Permission.ACC_INFINITE_CREDITS);"),
                "Renting must honor infinite-credit staff permission before charging");
        assertTrue(
                rentHandling.contains("!hasInfiniteCredits && user.getUserInfo().getCredits() < cost"),
                "Renting must reject non-staff users without enough credits for the computed cost");
        assertTrue(
                rentHandling.contains("user.applyCredits("),
                "Renting must deduct the computed credit cost, and know whether it went through");
        assertTrue(
                rentHandling.indexOf("user.applyCredits(") < rentHandling.indexOf("this.setRenterId"),
                "Credits must be charged before the rentable space is marked as rented");
    }
}
