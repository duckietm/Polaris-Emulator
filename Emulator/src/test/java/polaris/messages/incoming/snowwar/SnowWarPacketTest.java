package polaris.messages.incoming.snowwar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SnowWarPacketTest {

    @Test
    void acceptsTheFieldsWithOrWithoutTheTurnInts() {
        assertTrue(SnowWarPacket.hasInts(8, 2), "worldX, worldY");
        assertTrue(SnowWarPacket.hasInts(12, 2), "+ turn");
        assertTrue(SnowWarPacket.hasInts(16, 2), "+ turn, subturn");
        assertTrue(SnowWarPacket.hasInts(0, 0));
        assertTrue(SnowWarPacket.hasInts(8, 0), "make snowball with turn, subturn");
    }

    @Test
    void rejectsTruncatedOversizedOrRaggedPayloads() {
        // A truncated walk used to read as (x, 0).
        assertFalse(SnowWarPacket.hasInts(4, 2));
        assertFalse(SnowWarPacket.hasInts(20, 2));
        assertFalse(SnowWarPacket.hasInts(9, 2));
        assertFalse(SnowWarPacket.hasInts(7, 2));
    }

    @Test
    void acceptsChatOnlyWhenTheLengthPrefixMatchesThePayload() {
        assertTrue(SnowWarPacket.isWholeString(2 + 5, 5, 400));
        assertFalse(SnowWarPacket.isWholeString(2 + 5, 9, 400), "prefix longer than payload");
        assertFalse(SnowWarPacket.isWholeString(2 + 5 + 3, 5, 400), "trailing bytes");
        assertFalse(SnowWarPacket.isWholeString(2 + 5, 5, 4), "too long");
        assertFalse(SnowWarPacket.isWholeString(0, -1, 400), "empty payload");
    }

    @Test
    void validatesTrajectoriesAndArenaCoordinates() {
        for (int trajectory = 0; trajectory <= 3; trajectory++) {
            assertTrue(SnowWarPacket.isTrajectory(trajectory));
        }
        assertFalse(SnowWarPacket.isTrajectory(-1));
        assertFalse(SnowWarPacket.isTrajectory(4));

        assertTrue(SnowWarPacket.isInArena(50, 50, 0, 0));
        assertTrue(SnowWarPacket.isInArena(50, 50, 49 * 3200, 49 * 3200));
        assertTrue(SnowWarPacket.isInArena(50, 50, 50 * 3200, 50 * 3200));
        assertFalse(SnowWarPacket.isInArena(50, 50, -1, 100));
        assertFalse(SnowWarPacket.isInArena(50, 50, 100, 50 * 3200 + 1));
        assertFalse(SnowWarPacket.isInArena(50, 50, Integer.MAX_VALUE, 0));
    }
}
