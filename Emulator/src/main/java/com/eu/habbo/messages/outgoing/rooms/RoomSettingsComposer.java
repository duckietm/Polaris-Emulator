package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RoomSettingsComposer extends MessageComposer {
    private final Room room;

    public RoomSettingsComposer(Room room) {
        this.room = room;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomSettingsComposer);
        this.response.appendInt(this.room.getId());
        this.response.appendString(this.room.getName());
        this.response.appendString(this.room.getDescription());
        this.response.appendInt(this.room.getState().getState());
        this.response.appendInt(this.room.getCategory());
        this.response.appendInt(this.room.getUsersMax());
        this.response.appendInt(this.room.getUsersMax());

        if (!this.room.getTags().isEmpty()) {
            this.response.appendInt(this.room.getTags().split(";").length);
            for (String tag : this.room.getTags().split(";")) {
                this.response.appendString(tag);
            }
        } else {
            this.response.appendInt(0);
        }

        //this.response.appendInt(this.room.getRights().size());
        this.response.appendInt(this.room.getTradeMode()); //Trade Mode
        this.response.appendInt(this.room.isAllowPets() ? 1 : 0);
        this.response.appendInt(this.room.isAllowPetsEat() ? 1 : 0);
        this.response.appendInt(this.room.isAllowWalkthrough() ? 1 : 0);
        this.response.appendInt(this.room.isHideWall() ? 1 : 0);
        this.response.appendInt(this.room.getWallSize());
        this.response.appendInt(this.room.getFloorSize());

        this.response.appendInt(this.room.getChatMode());
        this.response.appendInt(this.room.getChatWeight());
        this.response.appendInt(this.room.getChatSpeed());
        this.response.appendInt(this.room.getChatDistance());
        this.response.appendInt(this.room.getChatProtection());

        this.response.appendBoolean(false); //IDK?

        this.response.appendInt(this.room.getMuteOption());
        this.response.appendInt(this.room.getKickOption());
        this.response.appendInt(this.room.getBanOption());
        this.response.appendInt(this.room.isAllowUnderpass() ? 1 : 0);
        this.response.appendInt(this.room.isMuteAllPets() ? 1 : 0);
        this.response.appendInt(this.room.isLeaveOnDoorTileEnabled() ? 1 : 0);
        this.response.appendInt(this.room.isIdleSleepEnabled() ? 1 : 0);
        this.response.appendInt(this.room.getIdleSleepTimeoutSeconds());
        this.response.appendInt(this.room.isIdleAutokickEnabled() ? 1 : 0);
        this.response.appendInt(this.room.getIdleAutokickTimeoutSeconds());
        return this.response;
    }

    public Room getRoom() {
        return room;
    }
}
