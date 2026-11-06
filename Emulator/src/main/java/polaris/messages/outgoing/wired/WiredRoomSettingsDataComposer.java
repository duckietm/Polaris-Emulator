package polaris.messages.outgoing.wired;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class WiredRoomSettingsDataComposer extends MessageComposer {
    private final Room room;
    private final User user;

    public WiredRoomSettingsDataComposer(Room room, User user) {
        this.room = room;
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredRoomSettingsDataComposer);

        int roomId = (this.room != null) ? this.room.getId() : 0;
        boolean canInspect = this.room != null && this.room.canInspectWired(this.user);
        boolean canModify = this.room != null && this.room.canModifyWired(this.user);
        boolean canManageSettings = this.room != null && this.room.canManageWiredSettings(this.user);
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
