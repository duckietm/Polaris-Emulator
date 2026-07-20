package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.messages.ServerMessage;
import java.util.Arrays;

final class RoomSerializer {

    private RoomSerializer() {}

    static void serialize(Room room, ServerMessage message) {
        message.appendInt(room.getId());
        message.appendString(room.getName());
        if (room.isPublicRoom()) {
            message.appendInt(0);
            message.appendString("");
        } else {
            message.appendInt(room.getOwnerId());
            message.appendString(room.getOwnerName());
        }
        message.appendInt(room.getState().getState());
        message.appendInt(room.getUserCount());
        message.appendInt(room.getUsersMax());
        message.appendString(room.getDescription());
        message.appendInt(0);
        message.appendInt(room.getScore());
        message.appendInt(0);
        message.appendInt(room.getCategory());

        String[] tags = Arrays.stream(room.getTags().split(";"))
                .filter(tag -> !tag.isEmpty())
                .toArray(String[]::new);
        message.appendInt(tags.length);
        for (String tag : tags) {
            message.appendString(tag);
        }

        int flags = 0;
        if (room.getGuildId() > 0) {
            flags |= 2;
        }
        if (room.isPromoted()) {
            flags |= 4;
        }
        if (!room.isPublicRoom()) {
            flags |= 8;
        }
        message.appendInt(flags);

        if (room.getGuildId() > 0) {
            appendGuild(room, message);
        }

        RoomPromotion promotion = room.getPromotionManager().getPromotion();
        if (room.getPromotionManager().getPromotedFlag() && promotion != null) {
            message.appendString(promotion.getTitle());
            message.appendString(promotion.getDescription());
            message.appendInt((promotion.getEndTimestamp() - room.currentUnixTimestamp()) / 60);
        }
    }

    private static void appendGuild(Room room, ServerMessage message) {
        Guild guild = room.gameEnvironment().getGuildManager().getGuild(room.getGuildId());
        if (guild != null) {
            message.appendInt(guild.getId());
            message.appendString(guild.getName());
            message.appendString(guild.getBadge());
        } else {
            message.appendInt(0);
            message.appendString("");
            message.appendString("");
        }
    }
}
