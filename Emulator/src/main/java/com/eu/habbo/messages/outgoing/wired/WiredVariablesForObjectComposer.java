package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.rooms.WiredObjectInspection;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Map;

/**
 * Official {@code WiredObjectInspectionData}: the type (0 furni, 1 user, -10 room), the furni's item id or the
 * user's room index (nothing for the room), {@code count x (variable id, value)}, and for a furni
 * {@code count x wired box id} of the boxes that pick it.
 */
public class WiredVariablesForObjectComposer extends MessageComposer {
    private final WiredObjectInspection inspection;

    public WiredVariablesForObjectComposer(WiredObjectInspection inspection) {
        this.inspection = inspection;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredVariablesForObjectComposer);
        int type = this.inspection.getType();
        this.response.appendInt(type);
        if (type == WiredObjectInspection.TYPE_FURNI || type == WiredObjectInspection.TYPE_USER) {
            this.response.appendInt(this.inspection.getObjectId());
        }
        this.response.appendInt(this.inspection.getValues().size());
        for (Map.Entry<String, Integer> value : this.inspection.getValues().entrySet()) {
            this.response.appendString(value.getKey());
            this.response.appendInt(value.getValue());
        }
        if (type == WiredObjectInspection.TYPE_FURNI) {
            this.response.appendInt(this.inspection.getConfiguredInWireds().size());
            for (int boxId : this.inspection.getConfiguredInWireds()) {
                this.response.appendInt(boxId);
            }
        }
        return this.response;
    }
}
