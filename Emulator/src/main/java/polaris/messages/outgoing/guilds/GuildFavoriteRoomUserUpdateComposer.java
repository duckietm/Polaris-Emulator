package polaris.messages.outgoing.guilds;

import polaris.hotel.guilds.Guild;
import polaris.hotel.rooms.RoomUnit;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class GuildFavoriteRoomUserUpdateComposer extends MessageComposer {
    private RoomUnit roomUnit;
    private Guild guild;

    public GuildFavoriteRoomUserUpdateComposer(RoomUnit roomUnit, Guild guild) {
        this.roomUnit = roomUnit;
        this.guild = guild;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.GuildFavoriteRoomUserUpdateComposer);
        this.response.appendInt(this.roomUnit.getId());
        this.response.appendInt(this.guild != null ? this.guild.getId() : 0);
        this.response.appendInt(this.guild != null ? this.guild.getState().state : 3);
        this.response.appendString(this.guild != null ? this.guild.getName() : "");
        return this.response;
    }

    public RoomUnit getRoomUnit() {
        return roomUnit;
    }

    public Guild getGuild() {
        return guild;
    }
}
