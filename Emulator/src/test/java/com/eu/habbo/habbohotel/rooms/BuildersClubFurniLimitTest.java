package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BuildersClubFurniLimitTest {
    @Test
    void trialUsersGetTheTrialLimitPlusBoughtFurni() {
        assertEquals(50, BuildersClubRoomSupport.furniLimit(false, 0, 0));
        assertEquals(95, BuildersClubRoomSupport.furniLimit(false, 1500, 45));
    }

    @Test
    void membersGetTheHighestPackageLimitNeverBelowTheMemberDefault() {
        assertEquals(500, BuildersClubRoomSupport.furniLimit(true, 0, 0));
        assertEquals(1000, BuildersClubRoomSupport.furniLimit(true, 1000, 0));
        assertEquals(1750, BuildersClubRoomSupport.furniLimit(true, 1500, 250));
    }

    @Test
    void packagesGiveAThousandForAMonthAndFifteenHundredForThreeMonths() {
        assertEquals(0, BuildersClubRoomSupport.packageFurniLimit(7));
        assertEquals(1000, BuildersClubRoomSupport.packageFurniLimit(31));
        assertEquals(1500, BuildersClubRoomSupport.packageFurniLimit(93));
        assertEquals(1500, BuildersClubRoomSupport.packageFurniLimit(365));
    }

    @Test
    void negativeBoughtFurniNeverLowersTheLimit() {
        assertEquals(500, BuildersClubRoomSupport.furniLimit(true, 0, -20));
    }
}
