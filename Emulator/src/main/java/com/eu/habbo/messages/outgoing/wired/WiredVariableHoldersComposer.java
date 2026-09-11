package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collections;
import java.util.List;

/**
 * Official AIR 13 variable-holders answer: the variable plus every holder id and value, used by the
 * overview tab to highlight the holders inside the room.
 */
public class WiredVariableHoldersComposer extends MessageComposer {
    private final int roomId;
    private final RoomWiredVariableCatalog.Variable variable;
    private final List<RoomWiredVariableCatalog.Holder> holders;

    public WiredVariableHoldersComposer(
            int roomId, RoomWiredVariableCatalog.Variable variable, List<RoomWiredVariableCatalog.Holder> holders) {
        this.roomId = roomId;
        this.variable = variable;
        this.holders = (holders != null) ? holders : Collections.emptyList();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredVariableHoldersComposer);
        this.response.appendInt(this.roomId);
        WiredAllVariablesDiffComposer.appendVariable(this.response, this.variable);

        this.response.appendInt(this.holders.size());
        for (RoomWiredVariableCatalog.Holder holder : this.holders) {
            this.response.appendInt(holder.getEntityId());
            this.response.appendInt(holder.getValue());
        }

        return this.response;
    }
}
