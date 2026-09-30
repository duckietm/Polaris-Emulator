package com.eu.habbo.messages.outgoing.users;

import com.eu.habbo.habbohotel.permissions.RankLimits;
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
        this.response.appendInt(
                RankLimits.favouriteRooms(this.habbo.getHabboInfo().getRank()));
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
