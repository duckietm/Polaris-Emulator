package com.eu.habbo.habbohotel.guilds;

import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.util.PacketGuard;

public final class GuildBadgeBuilder {
    public static final int MAX_BADGE_PARTS = 5;
    private static final int INTS_PER_PART = 3;
    private static final int BYTES_PER_INT = 4;
    private static final int MAX_PART_ID = 999;
    private static final int MAX_COLOR_ID = 99;
    private static final int MAX_POSITION = 8;

    private GuildBadgeBuilder() {
    }

    public static String readBadge(ClientMessage packet, int flatPartValueCount) {
        if (flatPartValueCount % INTS_PER_PART != 0) {
            return null;
        }

        int partCount = flatPartValueCount / INTS_PER_PART;
        if (!PacketGuard.isCountInRange(partCount, 1, MAX_BADGE_PARTS)
                || !PacketGuard.hasFixedWidthEntries(packet, flatPartValueCount, BYTES_PER_INT)) {
            return null;
        }

        StringBuilder badge = new StringBuilder(partCount * 6);
        for (int partIndex = 0; partIndex < partCount; partIndex++) {
            int id = packet.readInt();
            int color = packet.readInt();
            int position = packet.readInt();

            if (!isValidPart(id, color, position)) {
                return null;
            }

            badge.append(partIndex == 0 ? "b" : "s");
            badge.append(id < 100 ? "0" : "").append(id < 10 ? "0" : "").append(id);
            badge.append(color < 10 ? "0" : "").append(color);
            badge.append(position);
        }

        return badge.toString();
    }

    private static boolean isValidPart(int id, int color, int position) {
        return id >= 0 && id <= MAX_PART_ID
                && color >= 0 && color <= MAX_COLOR_ID
                && position >= 0 && position <= MAX_POSITION;
    }
}
