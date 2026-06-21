package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class FavoriteRoomsCountComposer extends MessageComposer {
    private final Habbo habbo;

    public FavoriteRoomsCountComposer(Habbo habbo) {
        this.habbo = habbo;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FavoriteRoomsCountComposer);
        this.response.appendInt(Emulator.getConfig().getInt("hotel.rooms.max.favorite"));
        this.response.appendInt(this.habbo.getHabboStats().getFavoriteRooms().size());
        for (int roomId : this.habbo.getHabboStats().getFavoriteRooms()) {
            this.response.appendInt(roomId);
        }
        return this.response;
    }

    public Habbo getHabbo() {
        return habbo;
    }
}
