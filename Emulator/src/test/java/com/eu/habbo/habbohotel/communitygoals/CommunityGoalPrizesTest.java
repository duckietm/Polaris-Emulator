package com.eu.habbo.habbohotel.communitygoals;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.outgoing.hotelview.CommunityGoalEarnedPrizesComposer;
import com.eu.habbo.messages.outgoing.hotelview.HallOfFameComposer;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The hall of fame and earned prizes of the AIR hotel view widgets, field by field. */
class CommunityGoalPrizesTest {

    @Test
    void theRankBucketsFollowTheRewardUserLimits() {
        int[] limits = {1, 4, 20};

        assertEquals(0, CommunityGoalManager.prizeLevelForRank(0, limits));
        assertEquals(1, CommunityGoalManager.prizeLevelForRank(1, limits));
        assertEquals(2, CommunityGoalManager.prizeLevelForRank(2, limits));
        assertEquals(2, CommunityGoalManager.prizeLevelForRank(5, limits));
        assertEquals(3, CommunityGoalManager.prizeLevelForRank(6, limits));
        assertEquals(3, CommunityGoalManager.prizeLevelForRank(25, limits));
        assertEquals(0, CommunityGoalManager.prizeLevelForRank(26, limits));
        assertEquals(0, CommunityGoalManager.prizeLevelForRank(1, new int[0]));
        assertEquals(0, CommunityGoalManager.prizeLevelForRank(1, null));
    }

    @Test
    void equalScoresShareARank() {
        assertArrayEquals(new int[] {1, 2, 2, 4}, CommunityGoalManager.competitionRanks(List.of(90, 50, 50, 10)));
        assertArrayEquals(new int[0], CommunityGoalManager.competitionRanks(List.of()));
    }

    @Test
    void aTrophyCarriesNameDateAndInscription() {
        assertEquals(
                "Ibuxal\t3-7-2026\tGold #1",
                CommunityGoalManager.trophyExtraData("Ibuxal", LocalDate.of(2026, 7, 3), "Gold #1"));
    }

    @Test
    void theHallOfFameIsWrittenTheWayAirReadsIt() {
        ByteBuf packet = new HallOfFameComposer(
                        "breedMonsterPlants",
                        List.of(new CommunityGoalHallOfFameEntry(7, "Ibuxal", "hd-180-1", 1, 12333)))
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals("breedMonsterPlants", readString(packet));
        assertEquals(1, packet.readInt());
        assertEquals(7, packet.readInt());
        assertEquals("Ibuxal", readString(packet));
        assertEquals("hd-180-1", readString(packet));
        assertEquals(1, packet.readInt());
        assertEquals(12333, packet.readInt());
        assertFalse(packet.isReadable());
        packet.release();
    }

    @Test
    void theEarnedPrizesAreWrittenTheWayAirReadsThem() {
        ByteBuf packet = new CommunityGoalEarnedPrizesComposer(
                        List.of(new CommunityGoalPrize(3, "breedMonsterPlants", 2, "ACH_Plants1", true, "Plant badge")))
                .compose()
                .get();
        packet.skipBytes(6);

        assertEquals(1, packet.readInt());
        assertEquals(3, packet.readInt());
        assertEquals("breedMonsterPlants", readString(packet));
        assertEquals(2, packet.readInt());
        assertEquals("ACH_Plants1", readString(packet));
        assertTrue(packet.readBoolean());
        assertEquals("Plant badge", readString(packet));
        assertFalse(packet.isReadable());
        packet.release();
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readShort();
        byte[] bytes = new byte[length];
        packet.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
