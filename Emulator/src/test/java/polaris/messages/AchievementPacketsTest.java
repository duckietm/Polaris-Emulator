package polaris.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.achievements.Achievement;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.achievements.TalentTrackLevel;
import polaris.hotel.achievements.TalentTrackType;
import polaris.hotel.items.Item;
import polaris.hotel.users.User;
import polaris.hotel.users.UserStats;
import polaris.messages.outgoing.achievements.AchievementListComposer;
import polaris.messages.outgoing.achievements.AchievementProgressComposer;
import polaris.messages.outgoing.achievements.AchievementUnlockedComposer;
import polaris.messages.outgoing.achievements.talenttrack.TalentLevelUpdateComposer;

class AchievementPacketsTest {
    @Test
    void listKeepsBothLegacyRecordsWholeBeforeAppendingKeyedStates() throws Exception {
        Achievement first = achievement(11, "First", 1);
        Achievement second = achievement(22, "Second", 2);
        AchievementManager manager = new AchievementManager();
        manager.getAchievements().put(first.name, first);
        manager.getAchievements().put(second.name, second);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getAchievementManager()).thenReturn(manager);
        User user = user();
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            ByteBuf packet = new AchievementListComposer(user).compose().get();
            try {
                packet.skipBytes(6);
                assertEquals(2, packet.readInt());
                readRecord(packet, 11, "First");
                readRecord(packet, 22, "Second");
                assertEquals("", string(packet));
                assertEquals(2, packet.readInt());
                assertEquals(11, packet.readInt());
                assertEquals(1, packet.readShort());
                assertEquals(22, packet.readInt());
                assertEquals(2, packet.readShort());
                assertFalse(packet.isReadable());
            } finally {
                packet.release();
            }
        }
    }

    @Test
    void progressAndUnlockUseActualRewardFieldsAndMetadata() throws Exception {
        Achievement achievement = achievement(11, "First", 4);
        User user = user();
        ByteBuf progress =
                new AchievementProgressComposer(user, achievement).compose().get();
        try {
            progress.skipBytes(6);
            readRecord(progress, 11, "First");
            assertEquals(4, progress.readShort());
            assertFalse(progress.isReadable());
        } finally {
            progress.release();
        }
        ByteBuf unlocked = new AchievementUnlockedComposer(user, achievement, achievement.firstLevel(), 77)
                .compose()
                .get();
        try {
            unlocked.skipBytes(6);
            assertEquals(11, unlocked.readInt());
            assertEquals(1, unlocked.readInt());
            assertEquals(77, unlocked.readInt());
            assertEquals("ACH_First1", string(unlocked));
            assertEquals(19, unlocked.readInt());
            assertEquals(7, unlocked.readInt());
            assertEquals(5, unlocked.readInt());
            assertEquals(0, unlocked.readInt());
            assertEquals(11, unlocked.readInt());
            assertEquals("", string(unlocked));
            assertEquals("identity", string(unlocked));
            assertTrue(unlocked.readBoolean());
            assertFalse(unlocked.isReadable());
        } finally {
            unlocked.release();
        }
    }

    @Test
    void talentLevelUpSendsPerkStringsAndFurnitureWithoutFakeClubDays() {
        TalentTrackLevel level = mock(TalentTrackLevel.class);
        level.level = 3;
        level.hcDays = 2;
        level.perks = new String[] {"TRADE", "HELPER"};
        Item item = mock(Item.class);
        when(item.getName()).thenReturn("welcome_chair");
        when(item.getSpriteId()).thenReturn(12345);
        level.items = Set.of(item);
        ByteBuf packet = new TalentLevelUpdateComposer(TalentTrackType.CITIZENSHIP, level)
                .compose()
                .get();
        try {
            packet.skipBytes(6);
            assertEquals("citizenship", string(packet));
            assertEquals(3, packet.readInt());
            assertEquals(2, packet.readInt());
            assertEquals("TRADE", string(packet));
            assertEquals("HELPER", string(packet));
            assertEquals(2, packet.readInt());
            assertEquals("welcome_chair", string(packet));
            assertEquals(0, packet.readInt());
            assertEquals("HABBO_CLUB", string(packet));
            assertEquals(2, packet.readInt());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void wiredAvailabilityListsTheConfiguredAchievementOfEveryGiveAchievementBox() {
        var wired = mock(polaris.hotel.items.interactions.wired.effects.WiredEffectGiveAchievement.class);
        when(wired.getAchievement()).thenReturn("WF_MyGame");
        when(wired.getAchievementCode()).thenReturn("WF_MyGame");
        var specialTypes = mock(polaris.hotel.rooms.RoomSpecialTypes.class);
        when(specialTypes.getEffects()).thenReturn(Set.of(wired));
        when(specialTypes.getTriggers()).thenReturn(Set.of());
        var room = mock(polaris.hotel.rooms.Room.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        ByteBuf packet = new polaris.messages.outgoing.wired.WiredEnvironmentComposer(room)
                .compose()
                .get();
        try {
            packet.skipBytes(6);
            assertFalse(packet.readBoolean());
            assertEquals(1, packet.readInt());
            assertEquals("WF_MyGame", string(packet));
            assertFalse(packet.isReadable());
            assertEquals("WF_MyGame", wired.getAchievementCode());
        } finally {
            packet.release();
        }
    }

    private static void readRecord(ByteBuf packet, int id, String name) {
        assertEquals(id, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals("ACH_" + name + "1", string(packet));
        assertEquals(0, packet.readInt());
        assertEquals(10, packet.readInt());
        assertEquals(7, packet.readInt());
        assertEquals(5, packet.readInt());
        assertEquals(0, packet.readInt());
        assertFalse(packet.readBoolean());
        assertEquals("identity", string(packet));
        assertEquals("group", string(packet));
        assertEquals(1, packet.readInt());
        assertEquals(1, packet.readInt());
    }

    private static Achievement achievement(int id, String name, int state) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getInt("id")).thenReturn(id);
        when(set.getString("name")).thenReturn(name);
        when(set.getString("category")).thenReturn("identity");
        when(set.getInt("level")).thenReturn(1);
        when(set.getInt("progress_needed")).thenReturn(10);
        when(set.getInt("reward_amount")).thenReturn(7);
        when(set.getInt("reward_type")).thenReturn(5);
        when(set.getInt("points")).thenReturn(19);
        when(set.getShort("state")).thenReturn((short) state);
        when(set.getInt("display_method")).thenReturn(1);
        when(set.getString("subcategory")).thenReturn("group");
        return new Achievement(set);
    }

    private static User user() {
        User user = mock(User.class);
        when(user.getUserStats()).thenReturn(mock(UserStats.class));
        return user;
    }

    private static String string(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
