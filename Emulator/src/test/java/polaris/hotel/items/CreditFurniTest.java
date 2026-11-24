package polaris.hotel.items;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** CFC_ credit furni are redeemable unless the hotel switches that off; CF_ furni always are. */
class CreditFurniTest {
    @Test
    void cfcFurniFollowTheHotelSetting() {
        assertTrue(CreditFurni.isRedeemableCreditName("CF_50_goldbar", true));
        assertTrue(CreditFurni.isRedeemableCreditName("CFC_500_goldbar", true));

        assertTrue(CreditFurni.isRedeemableCreditName("CF_50_goldbar", false));
        assertFalse(CreditFurni.isRedeemableCreditName("CFC_500_goldbar", false));

        assertFalse(CreditFurni.isRedeemableCreditName("chair_norja", true));
        assertFalse(CreditFurni.isRedeemableCreditName(null, true));
    }

    @Test
    void withoutASettingNothingChanges() {
        // No configuration loaded (as in this test): CFC_ furni stay redeemable.
        assertTrue(CreditFurni.cfcRedeemable());
    }

    @Test
    void theThreeRedeemPathsAskTheSameRule() throws Exception {
        for (String path : new String[] {
            "messages/incoming/rooms/items/RedeemItemEvent.java",
            "hotel/commands/RedeemCommand.java",
            "hotel/rooms/RoomTrade.java"
        }) {
            String source = Files.readString(Path.of("src/main/java/polaris/" + path));
            assertTrue(source.contains("CreditFurni.isRedeemableCreditName("), path);
        }
    }
}
