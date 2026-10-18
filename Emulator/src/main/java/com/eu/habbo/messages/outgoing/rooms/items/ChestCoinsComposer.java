package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * The coins in a coin chest (AIR {@code ChestCoins}): {@code int chestId, int coins, bool isUpdate}.
 *
 * <p>{@code isUpdate = false} answers the client's open request and opens the coin view;
 * {@code true} updates a view that is already open.
 */
public class ChestCoinsComposer extends MessageComposer {
    private final int chestId;
    private final int coins;
    private final boolean isUpdate;

    public ChestCoinsComposer(int chestId, int coins, boolean isUpdate) {
        this.chestId = chestId;
        this.coins = coins;
        this.isUpdate = isUpdate;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestCoinsComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.chestId));
        this.response.appendInt(this.coins);
        this.response.appendBoolean(this.isUpdate);
        return this.response;
    }
}
