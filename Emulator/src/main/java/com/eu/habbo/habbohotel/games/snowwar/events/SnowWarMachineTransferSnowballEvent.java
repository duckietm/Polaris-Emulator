package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/**
 * AIR event type 12: a machine or pile transferred a snowball to an avatar.
 */
public class SnowWarMachineTransferSnowballEvent extends SnowWarGameEvent {

    private final int avatarObjectId;
    private final int sourceObjectId;

    public SnowWarMachineTransferSnowballEvent(int avatarObjectId, int sourceObjectId) {
        super(TYPE_MACHINE_TRANSFER_SNOWBALL);
        this.avatarObjectId = avatarObjectId;
        this.sourceObjectId = sourceObjectId;
    }

    @Override
    protected void serializeFields(ServerMessage response) {
        response.appendInt(this.avatarObjectId);
        response.appendInt(this.sourceObjectId);
    }
}
