package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class WiredRoomSettingsDataComposer extends MessageComposer {
    private final Room room;
    private final Habbo habbo;

    public WiredRoomSettingsDataComposer(Room room, Habbo habbo) {
        this.room = room;
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredRoomSettingsDataComposer);

        int roomId = (this.room != null) ? this.room.getId() : 0;
        boolean canInspect = this.room != null && this.room.canInspectWired(this.habbo);
        boolean canModify = this.room != null && this.room.canModifyWired(this.habbo);
        boolean canManageSettings = this.room != null && this.room.canManageWiredSettings(this.habbo);
        int inspectMask = canInspect ? this.room.getWiredInspectMask() : 0;
        int modifyMask = canInspect ? this.room.getWiredModifyMask() : 0;

        this.response.appendInt(roomId);
        this.response.appendInt(inspectMask);
        this.response.appendInt(modifyMask);
        this.response.appendBoolean(canInspect);
        this.response.appendBoolean(canModify);
        this.response.appendBoolean(canManageSettings);
        // AIR 13 wired settings tab also restores the room's timezone picker.
        this.response.appendString((this.room != null) ? this.room.getWiredTimezone() : "");

        return this.response;
    }
}
