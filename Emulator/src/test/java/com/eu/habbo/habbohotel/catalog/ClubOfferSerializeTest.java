package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * A club offer reaches the client as months and days only (31-day months). A year has no field of its own, so it
 * must be counted as 12 months: a 600-day Builders Club add-on is shown as months * 31 + days = +600 furni.
 */
class ClubOfferSerializeTest {

    @Test
    void anAddonOfMoreThanAYearKeepsAllItsDays() throws Exception {
        int[] monthsAndDays = monthsAndDays(offer(600, "BUILDERS_CLUB_ADDON"));

        assertEquals(19, monthsAndDays[0]);
        assertEquals(11, monthsAndDays[1]);
        assertEquals(600, monthsAndDays[0] * 31 + monthsAndDays[1]);
    }

    @Test
    void shortOffersAreUnchanged() throws Exception {
        assertEquals(31, total(monthsAndDays(offer(31, "BUILDERS_CLUB"))));
        assertEquals(93, total(monthsAndDays(offer(93, "BUILDERS_CLUB"))));
        assertEquals(45, total(monthsAndDays(offer(45, "BUILDERS_CLUB_ADDON"))));
        assertEquals(250, total(monthsAndDays(offer(250, "BUILDERS_CLUB_ADDON"))));
    }

    @Test
    void aYearOfHabboClubIsTwelveMonths() throws Exception {
        int[] monthsAndDays = monthsAndDays(offer(372, "HC"));

        assertEquals(12, monthsAndDays[0]);
        assertEquals(0, monthsAndDays[1]);
    }

    private static int total(int[] monthsAndDays) {
        return monthsAndDays[0] * 31 + monthsAndDays[1];
    }

    private static ClubOffer offer(int days, String type) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getInt("id")).thenReturn(1);
        when(set.getString("name")).thenReturn("offer");
        when(set.getInt("days")).thenReturn(days);
        when(set.getInt("credits")).thenReturn(10);
        when(set.getInt("points")).thenReturn(0);
        when(set.getInt("points_type")).thenReturn(0);
        when(set.getString("type")).thenReturn(type);
        when(set.getString("deal")).thenReturn("0");
        when(set.getString("giftable")).thenReturn("0");
        return new ClubOffer(set);
    }

    /** id, name, unused bool, credits, points, points type, vip bool, then months and days. */
    private static int[] monthsAndDays(ClubOffer offer) {
        ServerMessage message = new ServerMessage(1);
        offer.serialize(message, 0);
        ByteBuf buffer = message.get();
        try {
            buffer.readerIndex(6);
            buffer.readInt();
            short nameLength = buffer.readShort();
            buffer.readCharSequence(nameLength, StandardCharsets.UTF_8);
            buffer.readByte();
            buffer.readInt();
            buffer.readInt();
            buffer.readInt();
            buffer.readByte();
            return new int[] {buffer.readInt(), buffer.readInt()};
        } finally {
            buffer.release();
        }
    }
}
