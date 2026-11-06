package polaris.hotel.users.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Trades, the marketplace and the recycler refuse rented furni; crafting, the secret recipe and both redeem paths
 * took them. The Builders Club placement fell back on the normal catalog's offers and placed any of them for free.
 */
class RentedAndBuildersClubFurniSpendTest {
    private static final Path MAIN = Path.of("src/main/java/polaris");

    private static String source(String path) throws Exception {
        return Files.readString(MAIN.resolve(path)).replaceAll("\\s+", " ");
    }

    @Test
    void crafingNeverSpendsARentedFurni() throws Exception {
        assertTrue(source("hotel/users/inventory/ItemsComponent.java")
                .contains("object.getBaseItem() == item && !object.hasRentPeriod()"));
        assertTrue(source("messages/incoming/crafting/CraftingCraftItemEvent.java")
                .contains(".getAndRemoveUnrentedUserItem(set.getKey())"));
        assertTrue(source("messages/incoming/crafting/CraftingCraftSecretEvent.java")
                .contains("userItem.hasRentPeriod()"));
    }

    @Test
    void redeemingRefusesRentedAndBuildersClubFurni() throws Exception {
        assertTrue(source("messages/incoming/rooms/items/RedeemItemEvent.java")
                .contains("item.hasRentPeriod() || BuildersClubRoomSupport.isTrackedItem(item.getId())"));
        assertTrue(source("hotel/commands/RedeemCommand.java").contains("|| item.hasRentPeriod()"));
    }

    @Test
    void buildersClubPlacementResolvesOnlyBuildersClubOffers() throws Exception {
        for (String event : new String[] {"BuildersClubPlaceRoomItemEvent", "BuildersClubPlaceWallItemEvent"}) {
            String source = source("messages/incoming/catalog/" + event + ".java");

            String compact = source.replace(" ", "");
            assertTrue(compact.contains(".buildersClubOfferDefs.get(offerId)"), event);
            assertFalse(compact.contains(".offerDefs.get(offerId)"), event);
        }
    }
}
