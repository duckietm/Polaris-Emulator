package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomSaveBehaviorTest {

    @Test
    void dirtyRoomSavesAllFieldsInTheEstablishedOrderThenBecomesClean() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource =
                new RoomJdbcTestSupport.RecordingDataSource();
        try (RoomJdbcTestSupport.InstalledDatabase ignored =
                     RoomJdbcTestSupport.install(dataSource)) {
            Room room = persistedRoom();
            room.setNeedsUpdate(true);

            room.save();
            room.save();

            assertEquals(1, dataSource.calls().size());
            RoomJdbcTestSupport.SqlCall call = dataSource.calls().getFirst();
            assertEquals("update", call.operation());
            assertTrue(call.sql().startsWith(
                    "UPDATE rooms SET name = ?, description = ?, password = ?"));
            assertTrue(call.sql().endsWith(
                    "idle_autokick_timeout_seconds = ? WHERE id = ?"));
            assertEquals(50, call.parameters().size());
            assertSavedValues(call.parameters());
        }
    }

    @Test
    void failedSaveKeepsTheRoomDirtyForTheNextAttempt() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource dataSource =
                new RoomJdbcTestSupport.RecordingDataSource();
        try (RoomJdbcTestSupport.InstalledDatabase ignored =
                     RoomJdbcTestSupport.install(dataSource)) {
            Room room = persistedRoom();
            room.setNeedsUpdate(true);
            dataSource.failUpdates(true);

            room.save();
            dataSource.failUpdates(false);
            room.save();
            room.save();

            assertEquals(2, dataSource.calls().size());
            assertEquals("update", dataSource.calls().get(0).operation());
            assertEquals("update", dataSource.calls().get(1).operation());
        }
    }

    @Test
    void saveUsesTheExplicitRoomDatabaseDependency() throws Exception {
        RoomJdbcTestSupport.RecordingDataSource expectedDataSource =
                new RoomJdbcTestSupport.RecordingDataSource();
        RoomJdbcTestSupport.RecordingDataSource legacyGlobalDataSource =
                new RoomJdbcTestSupport.RecordingDataSource();
        try (RoomJdbcTestSupport.InstalledDatabase ignored =
                     RoomJdbcTestSupport.install(legacyGlobalDataSource)) {
            Room room = persistedRoom(new RoomDependencies(
                    expectedDataSource::getConnection));
            room.setNeedsUpdate(true);

            room.save();

            assertEquals(1, expectedDataSource.calls().size());
            assertTrue(legacyGlobalDataSource.calls().isEmpty());
        }
    }

    private static Room persistedRoom() {
        return persistedRoom(RoomDependencies.runtime());
    }

    private static Room persistedRoom(RoomDependencies dependencies) {
        return RoomTestBuilder.room(41, 7, dependencies)
                .field("ownerName", "owner")
                .field("name", "Saved room")
                .field("description", "Persistence characterization")
                .field("password", "secret")
                .field("state", RoomState.PASSWORD)
                .field("usersMax", 25)
                .field("category", 3)
                .field("score", 88)
                .field("floorPaint", "1.1")
                .field("wallPaint", "2.2")
                .field("backgroundPaint", "3.3")
                .field("wallSize", 2)
                .field("wallHeight", -1)
                .field("floorSize", 4)
                .field("tags", "retro,polaris")
                .field("allowPets", true)
                .field("allowPetsEat", false)
                .field("allowWalkthrough", true)
                .field("hideWall", true)
                .field("chatMode", 1)
                .field("chatWeight", 2)
                .field("chatSpeed", 3)
                .field("chatDistance", 4)
                .field("chatProtection", 5)
                .field("muteOption", 1)
                .field("kickOption", 2)
                .field("banOption", 3)
                .field("pollId", 19)
                .field("guild", 23)
                .field("rollerSpeed", 4)
                .field("overrideModel", true)
                .field("staffPromotedRoom", true)
                .field("promoted", true)
                .field("tradeMode", 2)
                .field("moveDiagonally", true)
                .field("jukeboxActive", true)
                .field("hideWired", true)
                .field("allowUnderpass", true)
                .field("youtubeEnabled", true)
                .field("buildersClubTrialLocked", true)
                .field("buildersClubOriginalState", RoomState.LOCKED)
                .field("muteAllPets", true)
                .field("leaveOnDoorTileEnabled", true)
                .field("idleSleepEnabled", true)
                .field("idleSleepTimeoutSeconds", 120)
                .field("idleAutokickEnabled", true)
                .field("idleAutokickTimeoutSeconds", 600)
                .build();
    }

    private static void assertSavedValues(Map<Integer, Object> values) {
        assertEquals("Saved room", values.get(1));
        assertEquals("Persistence characterization", values.get(2));
        assertEquals("secret", values.get(3));
        assertEquals("password", values.get(4));
        assertEquals(25, values.get(5));
        assertEquals(3, values.get(6));
        assertEquals(88, values.get(7));
        assertEquals("1.1", values.get(8));
        assertEquals("2.2", values.get(9));
        assertEquals("3.3", values.get(10));
        assertEquals(2, values.get(11));
        assertEquals(-1, values.get(12));
        assertEquals(4, values.get(13));
        assertTrue(((String) values.get(14)).contains("#000000"));
        assertEquals("retro,polaris", values.get(15));
        assertEquals("1", values.get(16));
        assertEquals("0", values.get(17));
        assertEquals("1", values.get(18));
        assertEquals("1", values.get(19));
        assertEquals(1, values.get(20));
        assertEquals(2, values.get(21));
        assertEquals(3, values.get(22));
        assertEquals(4, values.get(23));
        assertEquals(5, values.get(24));
        assertEquals(1, values.get(25));
        assertEquals(2, values.get(26));
        assertEquals(3, values.get(27));
        assertEquals(19, values.get(28));
        assertEquals(23, values.get(29));
        assertEquals(4, values.get(30));
        assertEquals("1", values.get(31));
        assertEquals("1", values.get(32));
        assertEquals("1", values.get(33));
        assertEquals(2, values.get(34));
        assertEquals("1", values.get(35));
        assertEquals(7, values.get(36));
        assertEquals("owner", values.get(37));
        assertEquals("1", values.get(38));
        assertEquals("1", values.get(39));
        assertEquals("1", values.get(40));
        assertEquals("1", values.get(41));
        assertEquals("1", values.get(42));
        assertEquals("locked", values.get(43));
        assertEquals("1", values.get(44));
        assertEquals("1", values.get(45));
        assertEquals("1", values.get(46));
        assertEquals(120, values.get(47));
        assertEquals("1", values.get(48));
        assertEquals(600, values.get(49));
        assertEquals(41, values.get(50));
    }
}
