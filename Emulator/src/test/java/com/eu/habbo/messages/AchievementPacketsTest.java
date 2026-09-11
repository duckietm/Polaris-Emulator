package com.eu.habbo.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.achievements.TalentTrackLevel;
import com.eu.habbo.habbohotel.achievements.TalentTrackType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.messages.outgoing.achievements.AchievementListComposer;
import com.eu.habbo.messages.outgoing.achievements.AchievementProgressComposer;
import com.eu.habbo.messages.outgoing.achievements.AchievementUnlockedComposer;
import com.eu.habbo.messages.outgoing.achievements.talenttrack.TalentLevelUpdateComposer;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
        Habbo habbo = habbo();
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            ByteBuf packet = new AchievementListComposer(habbo).compose().get();
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
        Habbo habbo = habbo();
        ByteBuf progress =
                new AchievementProgressComposer(habbo, achievement).compose().get();
        try {
            progress.skipBytes(6);
            readRecord(progress, 11, "First");
            assertEquals(4, progress.readShort());
            assertFalse(progress.isReadable());
        } finally {
            progress.release();
        }
        ByteBuf unlocked = new AchievementUnlockedComposer(habbo, achievement, achievement.firstLevel(), 77)
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
    void wiredAvailabilityUsesTheAirCodeWhileKeepingTheConfiguredLookupName() {
        var wired = mock(com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveAchievement.class);
        when(wired.getAchievementCode()).thenReturn("WF_MyGame");
        var room = mock(com.eu.habbo.habbohotel.rooms.Room.class);
        when(room.getFloorItems()).thenReturn(Set.of(wired));
        ByteBuf packet = new com.eu.habbo.messages.outgoing.wired.WiredEnvironmentComposer(room)
                .compose()
                .get();
        try {
            packet.skipBytes(6);
            assertFalse(packet.readBoolean());
            assertEquals(1, packet.readInt());
            assertEquals("MyGame", string(packet));
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

    private static Habbo habbo() {
        Habbo habbo = mock(Habbo.class);
        when(habbo.getHabboStats()).thenReturn(mock(HabboStats.class));
        return habbo;
    }

    private static String string(ByteBuf packet) {
        return packet.readCharSequence(packet.readUnsignedShort(), StandardCharsets.UTF_8)
                .toString();
    }
}
