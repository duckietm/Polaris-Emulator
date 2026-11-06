package polaris.hotel.users.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BadgeRarityTest {

    @Test
    void mapsOwnerCountsToTheOfficialTierIds() {
        assertEquals(BadgeRarity.COMMON, BadgeRarity.tierForOwnerCount(0));
        assertEquals(BadgeRarity.UNIQUE, BadgeRarity.tierForOwnerCount(1));
        assertEquals(BadgeRarity.MYTHICAL, BadgeRarity.tierForOwnerCount(2));
        assertEquals(BadgeRarity.MYTHICAL, BadgeRarity.tierForOwnerCount(3));
        assertEquals(BadgeRarity.LEGENDARY, BadgeRarity.tierForOwnerCount(4));
        assertEquals(BadgeRarity.LEGENDARY, BadgeRarity.tierForOwnerCount(6));
        assertEquals(BadgeRarity.EPIC, BadgeRarity.tierForOwnerCount(7));
        assertEquals(BadgeRarity.EPIC, BadgeRarity.tierForOwnerCount(10));
        assertEquals(BadgeRarity.RARE, BadgeRarity.tierForOwnerCount(11));
        assertEquals(BadgeRarity.RARE, BadgeRarity.tierForOwnerCount(50));
        assertEquals(BadgeRarity.COMMON, BadgeRarity.tierForOwnerCount(51));
        assertEquals(BadgeRarity.COMMON, BadgeRarity.tierForOwnerCount(Integer.MAX_VALUE));
    }

    @Test
    void usesTheSameThresholdsAsTheBadgeLeaderboardEndpoint() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/polaris/networking/gameserver/badges/BadgeLeaderboardHttpHandler.java"));
        int classify = source.indexOf("private static Rarity classify(int ownerCount)");
        assertTrue(classify > -1, "leaderboard endpoint classifies badges by owner count");
        String body = source.substring(classify, source.indexOf('}', classify));
        assertTrue(body.contains("ownerCount > 50) return Rarity.COMMON"));
        assertTrue(body.contains("ownerCount > 10) return Rarity.RARE"));
        assertTrue(body.contains("ownerCount > 6) return Rarity.EPIC"));
        assertTrue(body.contains("ownerCount > 3) return Rarity.LEGENDARY"));
        assertTrue(body.contains("ownerCount > 1) return Rarity.MYTHICAL"));
        assertTrue(body.contains("ownerCount > 0) return Rarity.UNIQUE"));
    }
}
