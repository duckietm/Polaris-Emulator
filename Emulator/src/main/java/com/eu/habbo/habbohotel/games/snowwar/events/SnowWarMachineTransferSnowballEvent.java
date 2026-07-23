package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/**
 * Event type 7: a machine transferred a snowball to an avatar.
 */
public class SnowWarMachineTransferSnowballEvent extends SnowWarGameEvent {

    private final int avatarObjectId;
    private final int machineObjectId;

    public SnowWarMachineTransferSnowballEvent(int avatarObjectId, int machineObjectId) {
        super(TYPE_MACHINE_TRANSFER_SNOWBALL);
        this.avatarObjectId = avatarObjectId;
        this.machineObjectId = machineObjectId;
    }

    @Override
    protected void serializeFields(ServerMessage response) {
        response.appendInt(this.avatarObjectId);
        response.appendInt(this.machineObjectId);
    }
}
