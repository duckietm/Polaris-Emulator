package com.eu.habbo.messages.outgoing.housekeeping;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomState;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Arrays;

public class HousekeepingRoomDetailComposer extends MessageComposer {
    private final Room room;

    public HousekeepingRoomDetailComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HousekeepingRoomDetailComposer);

        if (this.room == null) {
            this.response.appendBoolean(false);
            return this.response;
        }

        this.response.appendBoolean(true);
        appendRoomFields(this.response, this.room);

        // Detail-only tail, read by the renderer as optional trailing fields. It
        // stays out of appendRoomFields because the room list repeats that block.
        this.response.appendInt(this.room.getCategory());
        this.response.appendInt(this.room.getTradeMode());
        this.response.appendInt(
                this.room.getState() != null ? this.room.getState().getState() : 0);

        String[] tags = splitTags(this.room.getTags());
        this.response.appendInt(tags.length);
        for (String tag : tags) {
            this.response.appendString(tag);
        }

        return this.response;
    }

    private static String[] splitTags(String tags) {
        if (tags == null || tags.isEmpty()) {
            return new String[0];
        }

        return Arrays.stream(tags.split(";"))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toArray(String[]::new);
    }

    /** Shared by HousekeepingRoomListComposer too. */
    public static void appendRoomFields(ServerMessage response, Room room) {
        response.appendInt(room.getId());
        response.appendString(safe(room.getName()));
        response.appendString(safe(room.getDescription()));
        response.appendInt(room.getOwnerId());
        response.appendString(safe(room.getOwnerName()));
        response.appendInt(room.getUserCount());
        response.appendInt(room.getUsersMax());
        response.appendBoolean(room.getState() != null && room.getState() != RoomState.OPEN);
        response.appendBoolean(room.isMuted());
        response.appendBoolean(room.isPublicRoom());
        response.appendInt(room.getDateCreated());
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
