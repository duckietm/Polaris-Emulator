package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.wired.arrays.WiredCreatorToolsArrayInspection;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class WiredArrayInspectionDataComposer extends MessageComposer {
    private final WiredCreatorToolsArrayInspection inspection;

    public WiredArrayInspectionDataComposer(WiredCreatorToolsArrayInspection inspection) {
        this.inspection = inspection;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredArrayInspectionDataComposer);
        this.response.appendString(WiredManager.getGson().toJson(this.inspection));
        return this.response;
    }
}
