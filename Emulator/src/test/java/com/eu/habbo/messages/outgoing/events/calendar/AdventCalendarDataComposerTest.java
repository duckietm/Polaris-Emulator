package com.eu.habbo.messages.outgoing.events.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.campaign.calendar.CalendarRewardClaimed;
import com.eu.habbo.habbohotel.campaign.calendar.CalendarRewardObject;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The calendar data also tells what every opened day held, after AIR's fields, so the boxes keep their picture. */
class AdventCalendarDataComposerTest {

    private static CalendarRewardObject reward(int id) throws SQLException {
        return reward(id, 0);
    }

    private static CalendarRewardObject reward(int id, int day) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getString(anyString())).thenReturn("");
        when(set.getString("product_name")).thenReturn("%credits% credits");
        when(set.getInt("id")).thenReturn(id);
        when(set.getInt("credits")).thenReturn(50);
        when(set.getInt("day")).thenReturn(day);
        return new CalendarRewardObject(set);
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readShort();
        byte[] bytes = new byte[length];
        packet.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static ArrayList<CalendarRewardClaimed> claims() {
        ArrayList<CalendarRewardClaimed> claims = new ArrayList<>();
        claims.add(new CalendarRewardClaimed(1, 1, 3, 9, new Timestamp(0)));
        // A claim whose reward is not in this campaign (another campaign, or a deleted reward) is left out.
        claims.add(new CalendarRewardClaimed(1, 2, 4, 77, new Timestamp(0)));
        return claims;
    }

    private static ByteBuf readUpToTheExtension(AdventCalendarDataComposer composer) {
        ByteBuf packet = composer.compose().get();
        packet.skipBytes(6);
        assertEquals("xmas", readString(packet));
        assertEquals("", readString(packet));
        assertEquals(5, packet.readInt());
        assertEquals(24, packet.readInt());
        assertEquals(2, packet.readInt());
        assertEquals(3, packet.readInt());
        assertEquals(4, packet.readInt());
        assertEquals(0, packet.readInt());
        return packet;
    }

    @Test
    void sendsTheRewardOfEveryOpenedDayOfTheCampaign() throws SQLException {
        ByteBuf packet = readUpToTheExtension(
                new AdventCalendarDataComposer("xmas", "", 24, 5, claims(), false, Map.of(9, reward(9)), null));

        assertEquals(1, packet.readInt());
        assertEquals(3, packet.readInt());
        assertEquals("50 credits", readString(packet));
        assertEquals("", readString(packet));
        assertEquals("", readString(packet));
        assertEquals("", readString(packet));
        assertEquals(50, packet.readInt());
        List.of(0, 0, 0).forEach(expected -> assertEquals(expected, packet.readInt()));
        assertEquals("", readString(packet));
        assertEquals(0, packet.readInt());
        assertFalse(packet.isReadable());
    }

    @Test
    void keepsAirsPacketForTheOldConstructor() {
        ByteBuf packet = readUpToTheExtension(new AdventCalendarDataComposer("xmas", "", 24, 5, claims(), false));

        assertFalse(packet.isReadable());
    }

    @Test
    void fallsBackToTheDaysRewardWhenTheClaimedRewardWasReplaced() throws SQLException {
        CalendarRewardClaimed claimed = new CalendarRewardClaimed(1, 1, 3, 9, new Timestamp(0));

        assertEquals(
                12,
                AdventCalendarDataComposer.rewardOf(claimed, Map.of(12, reward(12, 4), 13, reward(13, 5)))
                        .getId());
        assertEquals(null, AdventCalendarDataComposer.rewardOf(claimed, Map.of(13, reward(13, 5))));
    }
}
