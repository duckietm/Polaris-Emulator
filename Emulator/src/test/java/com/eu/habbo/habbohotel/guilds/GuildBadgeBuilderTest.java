package com.eu.habbo.habbohotel.guilds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.eu.habbo.messages.ClientMessage;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class GuildBadgeBuilderTest {
    @Test
    void buildsBadgeFromFlatPartTriplets() {
        ClientMessage packet = messageWithInts(
                1, 2, 4,
                35, 8, 0
        );

        assertEquals("b001024s035080", GuildBadgeBuilder.readBadge(packet, 6));
    }

    @Test
    void rejectsCountThatDoesNotRepresentCompleteTriplets() {
        ClientMessage packet = messageWithInts(1, 2, 4);

        assertNull(GuildBadgeBuilder.readBadge(packet, 4));
    }

    @Test
    void rejectsPayloadShorterThanDeclaredCount() {
        ClientMessage packet = messageWithInts(1, 2);

        assertNull(GuildBadgeBuilder.readBadge(packet, 3));
    }

    @Test
    void rejectsTooManyBadgeParts() {
        ClientMessage packet = messageWithInts(
                1, 1, 4,
                2, 1, 4,
                3, 1, 4,
                4, 1, 4,
                5, 1, 4,
                6, 1, 4
        );

        assertNull(GuildBadgeBuilder.readBadge(packet, 18));
    }

    @Test
    void rejectsPartValuesOutsideBadgeCodeRanges() {
        assertNull(GuildBadgeBuilder.readBadge(messageWithInts(1000, 1, 4), 3));
        assertNull(GuildBadgeBuilder.readBadge(messageWithInts(1, 100, 4), 3));
        assertNull(GuildBadgeBuilder.readBadge(messageWithInts(1, 1, 9), 3));
    }

    private static ClientMessage messageWithInts(int... values) {
        var buffer = Unpooled.buffer(values.length * Integer.BYTES);
        for (int value : values) {
            buffer.writeInt(value);
        }
        return new ClientMessage(0, buffer);
    }
}
