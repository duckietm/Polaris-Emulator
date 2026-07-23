package com.eu.habbo.habbohotel.games.snowwar.events;

import com.eu.habbo.messages.ServerMessage;

/**
 * Event type 6: a machine generated a new snowball.
 */
public class SnowWarMachineAddSnowballEvent extends SnowWarGameEvent {

    private final int machineObjectId;

    public SnowWarMachineAddSnowballEvent(int machineObjectId) {
        super(TYPE_MACHINE_ADD_SNOWBALL);
        this.machineObjectId = machineObjectId;
    }

    @Override
    protected void serializeFields(ServerMessage response) {
        response.appendInt(this.machineObjectId);
    }
}
